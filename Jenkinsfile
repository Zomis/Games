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
                sh 'docker stop $(docker ps -q --filter ancestor=gamesserver2)'
                sh 'docker rm -f $(docker ps -q --filter ancestor=gamesserver2)'
                sh 'docker build . -t gamesserver2'
                sh 'docker run -d -p 127.0.0.1:8082:8081 -v /home/zomis/jenkins/gamesserver2:/data/logs -w /data/logs gamesserver2'
            }
        }

        stage('Results') {
            steps {
/*
                withSonarQubeEnv('My SonarQube Server') {
                    // requires SonarQube Scanner for Maven 3.2+
                    sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar'
                }
*/
            }
        }
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
