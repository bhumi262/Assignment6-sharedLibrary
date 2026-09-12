def call(String configFile = 'config.properties') {
    pipeline {
        agent any

        stages {

            stage('Clone') {
                steps {
                    checkout scm
                }
            }

            stage('Load Config') {
                steps {
                    script {
                        def props = readProperties file: configFile
                        env.SLACK_CHANNEL_NAME  = props.SLACK_CHANNEL_NAME
                        env.ENVIRONMENT         = props.ENVIRONMENT
                        env.CODE_BASE_PATH      = props.CODE_BASE_PATH
                        env.ACTION_MESSAGE      = props.ACTION_MESSAGE
                        env.KEEP_APPROVAL_STAGE = props.KEEP_APPROVAL_STAGE
                    }
                }
            }

            stage('User Approval') {
                when {
                    expression { env.KEEP_APPROVAL_STAGE.toBoolean() }
                }
                steps {
                    input message: "Deploy Redis (${env.ENVIRONMENT}) — Approve?"
                }
            }

            stage('Playbook Execution') {
                steps {
                    sh "ansible-playbook -i inventory.ini site.yml"
                }
            }

            stage('Notification') {
                steps {
                    slackSend(channel: env.SLACK_CHANNEL_NAME,
                              message: env.ACTION_MESSAGE)
                }
            }
        }

        post {
            failure {
                slackSend(channel: env.SLACK_CHANNEL_NAME,
                          message: "❌ Redis deployment failed: ${env.ACTION_MESSAGE}")
            }
            success {
                slackSend(channel: env.SLACK_CHANNEL_NAME,
                          message: "✅ Redis deployment success: ${env.ACTION_MESSAGE}")
            }
        }
    }
}