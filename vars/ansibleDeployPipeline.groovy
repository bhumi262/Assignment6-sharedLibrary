def call(String configFile = 'redis.properties') {
    pipeline {
        agent any

        environment {
            ANSIBLE_HOST_KEY_CHECKING = 'False'
        }

        stages {
            stage('Clone') {
                steps {
                    sh 'rm -rf repo'
                    sh 'git clone https://github.com/bhumi262/AnsibleRedis.git repo'
                }
            }

            stage('Load Config') {
                steps {
                    script {
                        def configText = libraryResource(configFile)
                        def props = [:]
                        configText.readLines().each { line ->
                            line = line.trim()
                            if (line && !line.startsWith('#') && line.contains('=')) {
                                def parts = line.split('=', 2)
                                props[parts[0].trim()] = parts[1].trim()
                            }
                        }
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

            stage('Check Ansible') {
                steps {
                   sh 'ansible --version'
                }
            }

            stage('Playbook Execution') {
                steps {
                    dir("repo/${env.CODE_BASE_PATH}") {
                        withCredentials([sshUserPrivateKey(credentialsId: 'redis-ec2-key', keyFileVariable: 'SSH_KEY')]) {
                            sh "ansible-playbook -i inventory.ini site.yml --private-key=\$SSH_KEY"
                        }
                    }
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
