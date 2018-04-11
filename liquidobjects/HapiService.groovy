package liquidobjects

class HapiService {

    boolean separateBuildAndAppImage = true //Dockerfile-build present
    boolean bddTests = false //No Dockerfile-bdd
    boolean unitTests = false //No Dockerfile-test
    boolean isSubFolder = false

    String componentName = ""
    String serviceNameOverride = ""
    String applicationRepoURL
    String applicationRepoBranch = "master"
    String dockerContainerName = "default"
    String dockerRepositoryURI
    String dockerImageTag = "latest"
    String awsRegion = "us-west-2"
    String deploymentServerElasticIPAddress
    String environmentFolderName = "/workspace/LiquidStudio/Studio_Session/Environment/Instance/Create_Docker_Server" // name of env folder where certs are stored
    String uniqueEnvName

    // docker port mappings
    String hostPort = "8081"
    String containerPort = "8081"

    def environmentList = ["CI", "DEV"]

}
