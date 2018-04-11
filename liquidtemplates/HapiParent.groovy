package liquidtemplates

class HapiParent {

  HapiParent(def jenkins, def workspaceFolderName, def projectFolderName, def jobs) {
      // Folders
      def projectNameKey = projectFolderName.toLowerCase().replace("/", "-");
      // Jobs
      def buildDeploy = jenkins.freeStyleJob(projectFolderName + "/HapiServices/Overview_Pipeline_Job")
      // Views
      def pipeline = jenkins.buildPipelineView(projectFolderName + "/HapiServices/Overview_Pipeline")
      pipeline.with {
          title('Overview Pipeline')
          description("This is overview pipeline for all jobs")
          displayedBuilds(5)
          selectedJob(projectFolderName + "/HapiServices/Overview_Pipeline_Job")
          showPipelineParameters()
          showPipelineDefinitionHeader()
          refreshFrequency(5)
      }

      buildDeploy.with {
          description("This job is used to generate pipeline view and kickoff all microservices")
          publishers {
              downstreamParameterized {
                for(job in jobs) {
                  def subFolder = "";
                  if(job.isSubFolder) {
                    subFolder = job.componentName + "/";
                  }
                  trigger(projectFolderName + "/HapiServices/" + subFolder + job.componentName + "_Build") {
                      condition("UNSTABLE_OR_BETTER")
                      triggerWithNoParameters()
                  }
                }
              }
          }
      }

  }


}