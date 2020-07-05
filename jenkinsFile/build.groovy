import groovy.json.JsonOutput
import hudson.tasks.test.AbstractTestResultAction
import hudson.model.Actionable
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException


def get_tag(){
  def python_file = libraryResource 'corpora/tag.py'

  def animal_corpus = libraryResource('corpora/animals.json')

  def dinosours_corpus = libraryResource('corpora/dinosaurs.json')

  def adjs_corpus = libraryResource('corpora/adjs.json')

  def moods_corpus = libraryResource('corpora/moods.json')

  writeFile(file: 'tag.py', text: python_file)
  writeFile(file: 'adjs.json', text: adjs_corpus)
  writeFile(file: 'animals.json', text: animal_corpus)
  writeFile(file: 'dinosaurs.json', text: dinosours_corpus)
  writeFile(file: 'moods.json', text: moods_corpus)

}

def notifySlack(text,slack_url, channel, attachments) {
    def slackURL = slack_url
    def jenkinsIcon = 'https://wiki.jenkins-ci.org/download/attachments/2916393/logo.png'

    def payload = JsonOutput.toJson([text: text,
        channel: channel,
        username: "Jenkins",
        icon_url: jenkinsIcon,
        attachments: attachments
    ])

    sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
}

def call(Map config){
    node('master'){

          def String ecr
          def String environment

          def String bucket
          def fullName = "${env.JOB_NAME}".split('/')
          def projectName = fullName[0]
          def branch = fullName[1]

          def String slack_url
          def String channel

          def String tag
          def String git_commit
          def String git_author_name
          def String git_commit_message

          try {
              stage('checkout') {


                  slack_url = config.slack_url
                  channel = config.channel


                  checkout([
                          $class                           : 'GitSCM',
                          branches                         : scm.branches,
                          doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                          extensions                       : scm.extensions + [[$class: 'CloneOption', noTags: false, reference: '', shallow: true]],
                          submoduleCfg                     : [],
                          userRemoteConfigs                : scm.userRemoteConfigs
                  ])
                  git_commit = sh(returnStdout: true, script: 'git show -s --format=\'%h\'')
                  git_author_name = sh(returnStdout: true, script: 'git --no-pager show -s --format=\'<%ae>\' ${git_commit}')
                  git_commit_message = sh(returnStdout: true, script: 'git show -s --format=\'%s\' ${git_commit}')

                  notifySlack("Build Started!", slack_url, channel, [
                          [
                                  title      : "${projectName} build #${env.BUILD_NUMBER}",
                                  title_link : "${env.BUILD_URL}",
                                  color      : "#0000FF",
                                  text       : "${git_author_name}",
                                  "mrkdwn_in": ["fields"],
                                  fields     : [
                                          [
                                                  title: "Branch",
                                                  value: "${branch}",
                                                  short: true
                                          ],
                                          [
                                                  title: "Commit",
                                                  value: "${git_commit}",
                                                  short: true
                                          ],
                                          [
                                                  title: "Author Name",
                                                  value: "${git_author_name}",
                                                  short: false
                                          ]
                                  ]
                          ]
                  ])

              }


              stage('get dynamic tag') {
                  environment = config.environment
                  bucket = config.bucket
                  get_tag()
                  sh("python3 tag.py ${projectName} ${environment} ${bucket}> tag.txt")
                  tag = readFile(file: 'tag.txt').trim()

              }

              stage('docker build') {
                  ecr = config.ecr
                  def commit_message_without_space = git_commit_message.replaceAll(' ', '_')
                  print commit_message_without_space


                  sh """
                  echo "LABEL COMMIT_AUTHOR=${git_author_name}" >> ${WORKSPACE}/Dockerfile
                    echo "LABEL BUILD_TAG=${env.BUILD_TAG}" >> ${WORKSPACE}/Dockerfile
                     echo "LABEL GIT_BRANCH=${branch}" >> ${WORKSPACE}/Dockerfile
                      echo "LABEL GIT_COMMIT=${git_commit}" >> ${WORKSPACE}/Dockerfile
                        docker build -t $ecr:$tag .
                    """


              }

              stage('docker tag & push') {

                  ecr = config.ecr
                  sh """
               docker tag $ecr:$tag $ecr:$git_commit
               docker tag $ecr:$tag $ecr:latest

               docker push $ecr:$tag
              docker push $ecr:$git_commit
              docker push $ecr:latest"""


              }
              notifySlack("Build Passed!", slack_url, channel, [
                      [
                              title      : "${projectName} build #${env.BUILD_NUMBER}",
                              title_link : "${env.BUILD_URL}",
                              color      : "good",
                              text       : "${git_author_name}",
                              "mrkdwn_in": ["fields"],
                              fields     : [
                                      [
                                              title: "Branch",
                                              value: "${branch}",
                                              short: true
                                      ],
                                      [
                                              title: "Commit",
                                              value: "${git_commit}",
                                              short: false
                                      ],
                                      [
                                              title: "Author Name",
                                              value: "${git_author_name}",
                                              short: false
                                      ],
                                      [
                                              title: "Custom Tag",
                                              value: "${tag}",
                                              short: false
                                      ],
                                      [
                                              title: "ECR",
                                              value: "${ecr}",
                                              short: false
                                      ]

                              ]
                      ]
              ])
          }catch (FlowInterruptedException interruptEx) {
              notifySlack("Build Aborted!", slack_url, channel, [
                      [
                              title      : "${projectName} build #${env.BUILD_NUMBER}",
                              title_link : "${env.BUILD_URL}",
                              color      : "#000000",
                              text       : "${git_author_name}",
                              "mrkdwn_in": ["fields"],
                              fields     : [
                                      [
                                              title: "Branch",
                                              value: "${branch}",
                                              short: true
                                      ],
                                      [
                                              title: "Commit",
                                              value: "${git_commit} ",
                                              short: true
                                      ],
                                      [
                                              title: "Author Name",
                                              value: "${git_author_name}",
                                              short: false
                                      ]
                              ]
                      ]
              ])
          }
          catch (Exception e) {
              notifySlack("Build Failed!", slack_url, channel, [
                      [
                              title      : "${projectName} build #${env.BUILD_NUMBER}",
                              title_link : "${env.BUILD_URL}",
                              color      : "danger",
                              text       : "${git_author_name}",
                              "mrkdwn_in": ["fields"],
                              fields     : [
                                      [
                                              title: "Branch",
                                              value: "${branch}",
                                              short: true
                                      ],
                                      [
                                              title: "Commit",
                                              value: "${git_commit}",
                                              short: true
                                      ],
                                      [
                                              title: "Author Name",
                                              value: "${git_author_name}",
                                              short: false
                                      ],
                                      [
                                              title: "Error",
                                              value: "${e}",
                                              short: false
                                      ]
                              ]
                      ]
              ])
          }
    }

}

