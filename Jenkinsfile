#!/usr/bin/env groovy

@Library('ZomisJenkins')
import net.zomis.jenkins.Duga

pipeline {
    agent any

    stages {
        stage('Prepare') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew test :games-server:assemble'
                // Don't clean to hopefully only build Docker-file if changes are made.
            }
        }

        stage('Docker Image') {
            steps {
                script {
                    // Stop running containers
                    sh 'docker ps -q --filter name="games_server" | xargs -r docker stop'
                    sh 'docker ps -q --filter name="games_client" | xargs -r docker stop'

                    sh 'docker build . -t gamesserver2'
                    sh 'docker run -d --rm --name games_server -p 192.168.0.110:8082:8081 -v /home/zomis/jenkins/gamesserver2:/data/logs -v /etc/localtime:/etc/localtime:ro -w /data/logs gamesserver2'
                    sh 'docker run -d --rm --name games_client -v $(pwd):/src -w /src/games-vue-client -p 42637:42637 node:6 bash -c "npm install && npm run dev"'
                }
            }
        }

/*
                withSonarQubeEnv('My SonarQube Server') {
                    // requires SonarQube Scanner for Maven 3.2+
                    sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar'
                }
*/
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/build/test-results/junit-platform/TEST-*.xml'
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
