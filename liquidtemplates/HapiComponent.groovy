package liquidtemplates
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

class HapiComponent {

    def jenkins
    def workspaceFolderName
    def projectFolderName
    def service
    def projectNameKey
    def jobSubFolderPath = ""
    def microserviceJobFolder = "HapiServices"

    HapiComponent(def jenkins, def workspaceFolderName, def projectFolderName, def service) {
    
        this.jenkins = jenkins
        this.workspaceFolderName = workspaceFolderName
        this.projectFolderName = projectFolderName
        this.service = service

        projectNameKey = projectFolderName.toLowerCase().replace("/", "-");

        //get the name of sub-folder for each service
        if(service.isSubFolder) {
            jenkins.folder(projectFolderName + "/"+microserviceJobFolder){}
            jenkins.folder(projectFolderName + "/"+microserviceJobFolder+"/" + service.componentName){}
            jobSubFolderPath = "/"+microserviceJobFolder+"/" + service.componentName
        }

        def buildJob = jenkins.freeStyleJob(projectFolderName + jobSubFolderPath + "/${service.componentName}_Build")
        buildApp(buildJob)

        def deployJob = jenkins.freeStyleJob(projectFolderName + jobSubFolderPath + "/${service.componentName}_Deploy")
        deployApp(deployJob)

        def pipelineView = jenkins.buildPipelineView(projectFolderName + jobSubFolderPath + "/${service.componentName}_Pipeline")

        pipelineView.with {
            title("ADOP ${service.componentName} Pipeline")
            displayedBuilds(5)
            selectedJob(projectFolderName + jobSubFolderPath + "/${service.componentName}_Build")
            showPipelineParameters()
            showPipelineDefinitionHeader()
            refreshFrequency(5)
        }

      }

      void buildApp(def buildAppJob) {

         buildAppJob.with {

          description("Build ${service.componentName} service")
          wrappers {
            preBuildCleanup()
            credentialsBinding {
              usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
              sshAgent('adop-jenkins-master')
            }
          }
          environmentVariables {
             env('IMAGE', service.dockerRepositoryURI)
             env('IMAGE_TAG', service.dockerImageTag)
             env('BUILD_IMAGE', 'build-image')
             env('BUILD_CONTAINER', 'build-image-container')
             env('AWS_REGION', service.awsRegion)
          }
          label("docker")
          triggers {
            scm("H/1 * * * *")
          }
          scm {
            git {
              remote {
                url(service.applicationRepoURL)
                credentials("adop-jenkins-master")
              }
              branch("*/${service.applicationRepoBranch}")
            }
          }
          steps {
            if(service.separateBuildAndAppImage) {
              shell('''
                  |echo "creating build image and then building app image"
                  |
                  |mv config.json.example config.json
                  |
                  |docker build -f Dockerfile-build -t ${BUILD_IMAGE} .
                  |
                  |if [[ $(docker ps -a | grep ${BUILD_CONTAINER}) ]]; then
                  |   docker rm -f ${BUILD_CONTAINER}
                  |fi 
                  |
                  |docker run --name ${BUILD_CONTAINER} build-image 
                  |
                  |docker cp ${BUILD_CONTAINER}:/code/build .
                  |docker build -f Dockerfile-app -t ${IMAGE}:${IMAGE_TAG} .
                  |
                  |DOCKER_LOGIN=$(aws ecr get-login --region $AWS_REGION)
                  |${DOCKER_LOGIN}
                  |
                  |docker push ${IMAGE}:${IMAGE_TAG}
                  |
                  |'''.stripMargin()
              )
            } else {
              shell('''
                  |echo "building docker image"
                  |
                  |mv config.json.example config.json
                  |
                  |docker build -t ${IMAGE}:${IMAGE_TAG} .
                  |
                  |DOCKER_LOGIN=$(aws ecr get-login --region $AWS_REGION)
                  |${DOCKER_LOGIN}
                  |
                  |docker push ${IMAGE}:${IMAGE_TAG}
                  |
                  |'''.stripMargin()
              )
            }
          }
          publishers {
             downstreamParameterized {
                trigger(projectFolderName + jobSubFolderPath + "/${service.componentName}_Deploy") {
                    condition("SUCCESS")
                    triggerWithNoParameters()
                }
             }
          }

         }

      }

      void deployApp(def deployAppJob) { // deploy to DEV 

        deployAppJob.with {

          description("Deploy ${service.componentName} service")
          wrappers {
            preBuildCleanup()
            credentialsBinding {
              usernamePassword('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY', 'AWS_ENVKEY_' + projectFolderName.replaceAll("[^a-zA-Z0-9]+","_"))
              sshAgent('adop-jenkins-master')
            }
          }
          environmentVariables {
             env('HOST_PORT', service.hostPort)
             env('CONTAINER_PORT', service.containerPort)
             env('CONTAINER_NAME', service.dockerContainerName)
             env('IMAGE', service.dockerRepositoryURI+':'+service.dockerImageTag)
             env('ENV_IP', service.deploymentServerElasticIPAddress)
             env('AWS_REGION', service.awsRegion)
             env('ENV', service.environmentList[1]) // DEV 
             env('ENV_FOLDER', service.environmentFolderName)
             env('ENV_NAME', service.uniqueEnvName)
             env('DOCKER_BINARY', 'docker/docker')
          }
          label("docker")
          steps {
            shell('''
                |
                |DOCKERSSLDIR=$(find ${ENV_FOLDER}/${ENV_NAME} -type d -name certs)
                |
                |wget -q https://get.docker.com/builds/Linux/x86_64/docker-latest.tgz
                |tar -xvzf docker-latest.tgz
                |${DOCKER_BINARY} -v
                |
                |DOCKER_PARAMS="--tlsverify --tlscacert=${DOCKERSSLDIR}/ca.pem --tlscert=${DOCKERSSLDIR}/cert.pem --tlskey=${DOCKERSSLDIR}/key.pem -H=${ENV_IP}:2376"
                |echo "DOCKER_PARAMS=${DOCKER_PARAMS}"> $WORKSPACE/dockerparams.properties
                |
                |# check whether container exists
                |
                |if [[ $(${DOCKER_BINARY} ${DOCKER_PARAMS} ps -a | grep ${CONTAINER_NAME}) ]]; then
                |    ${DOCKER_BINARY} ${DOCKER_PARAMS} rm -f ${CONTAINER_NAME}
                |fi
                |
                |# login to ecr 
                |
                |DOCKER_LOGIN=$(aws ecr get-login --region $AWS_REGION)
                |${DOCKER_LOGIN}
                |
                |# deploy container 
                |
                |${DOCKER_BINARY} ${DOCKER_PARAMS} pull ${IMAGE}
                |${DOCKER_BINARY} ${DOCKER_PARAMS} run -d --name ${CONTAINER_NAME} -p ${HOST_PORT}:${CONTAINER_PORT} -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} -e PORT=${CONTAINER_PORT} -e NODE_ENV=${ENV} ${IMAGE}
                |
                '''.stripMargin()
            )
          }

        }

      }

}