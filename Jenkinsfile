#!/usr/bin/env groovy

@Library('ZomisJenkins')
import net.zomis.jenkins.Duga

pipeline {
    agent any

    options {
        timeout(time: 1, unit: 'HOURS')
    }
    tools {
        jdk 'Java11'
    }

    stages {
        stage('Environment Vars') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    sh 'rm -f .env.local'
                    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('UTC'))
                    sh "echo 'VUE_APP_BUILD_TIME=$timestamp' >> .env.local"
                    sh "echo 'VUE_APP_BUILD_NUMBER=$env.BUILD_NUMBER' >> .env.local"
                    sh "echo 'VUE_APP_GIT_COMMIT=$env.GIT_COMMIT' >> .env.local"
                    sh "echo 'VUE_APP_GIT_BRANCH=$env.GIT_BRANCH' >> .env.local"
                    sh 'cat .env.local'
                    sh 'cp .env.local games-vue-client/'
                }
            }
        }
        stage('Build Server') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                sh 'cp /home/zomis/jenkins/server2-secrets.properties games-server/src/main/resources/secrets.properties'
                sh 'cp /home/zomis/jenkins/server2-startup.conf docker/server2.conf.docker'
                sh './gradlew clean test shadowCreate --info'
                script {
                    def gitChanges = sh(script: 'git diff-index HEAD', returnStatus: true)
                    if (gitChanges) {
                        error("There are git changes after build")
                    }
                }
                sh 'cp build/libs/*-all.jar docker/'
            }
        }
        stage('Client npm install') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('games-vue-client') {
                    sh 'npm install'
                }
            }
        }
        stage('Client lint') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('games-vue-client') {
                    sh 'npm run validate'
                }
            }
        }
        stage('Build Client') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                // sh 'cp games-js/.eslintrc.js games-js/web/'
                dir('games-vue-client') {
                    sh 'npm run build'
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    // Stop running containers
                    sh 'docker ps -q --filter name="games_server" | xargs -r docker stop'

                    // Deploy server
                    sh 'docker build ./docker/ -t gamesserver2'
                    withCredentials([usernamePassword(
                          credentialsId: 'AWS_CREDENTIALS',
                          passwordVariable: 'AWS_SECRET_ACCESS_KEY',
                          usernameVariable: 'AWS_ACCESS_KEY_ID')]) {
                        withEnv(["ENV_AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}", "ENV_AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}"]) {

                            def result = sh(script: """docker run --network host -d --rm --name games_server \
                              -e TZ=Europe/Amsterdam \
                              -e AWS_SECRET_ACCESS_KEY=$ENV_AWS_SECRET_ACCESS_KEY \
                              -e AWS_ACCESS_KEY_ID=$ENV_AWS_ACCESS_KEY_ID \
                              -v /etc/letsencrypt:/etc/letsencrypt \
                              -v /home/zomis/jenkins/gamesserver2:/data/logs \
                              -v /etc/localtime:/etc/localtime:ro \
                              -w /data/logs gamesserver2""",
                                returnStdout: true)
                            println(result)
                        }
                    }

                    // Deploy client
                    sh 'rm -rf /home/zomis/docker-volumes/games-vue-client'
                    sh 'cp -r $(pwd)/games-vue-client/dist /home/zomis/docker-volumes/games-vue-client'
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'build/test-results/jvmTest/TEST-*.xml'
        }
        success {
            zpost(0)
        }
        unstable {
            zpost(1)
        }
        failure {
            zpost(2)
        }
    }
}
