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
                sh './gradlew clean test :games-server:assemble'
            }
        }

        stage('Docker Image') {
            steps {
                script {
                    def ps = sh(script: 'docker ps -q --filter ancestor=gamesserver2', returnStdout: true)
                    echo "Result from docker ps: '$ps'"
                    if (ps && !ps.isEmpty()) {
                        sh "docker stop $ps"
                        sh "docker rm -f $ps"
                    }
                    sh 'docker build . -t gamesserver2'
                    sh 'docker run -d -p 192.168.0.110:8082:8081 -v /home/zomis/jenkins/gamesserver2:/data/logs -v /etc/localtime:/etc/localtime:ro -w /data/logs gamesserver2'
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
