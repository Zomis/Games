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
                sh 'cp /home/zomis/jenkins/server2-secrets.properties games-server/src/main/resources/secrets.properties'
                sh './gradlew clean test :games-server:assemble :games-js:assemble'
                dir('games-vue-client') {
                    sh 'npm run build'
                }
            }
        }

        stage('Docker Image') {
            when {
                branch 'master'
            }
            steps {
                script {
                    // Stop running containers
                    sh 'docker ps -q --filter name="games_server" | xargs -r docker stop'
                    sh 'docker ps -q --filter name="games_client" | xargs -r docker stop'

                    sh 'docker build . -t gamesserver2'
                    sh 'docker run -d --rm --name games_server -p 192.168.0.110:8082:8081 -v /home/zomis/jenkins/gamesserver2:/data/logs -v /etc/localtime:/etc/localtime:ro -w /data/logs gamesserver2'
                    sh 'docker run -d --rm --name games_client -v $(pwd)/games-vue-client/dist:/usr/share/nginx/html:ro -p 42637:80 nginx'
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
