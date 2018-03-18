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
                sh './gradlew assemble'
            }
        }
        stage('Results') {
            steps {
                junit allowEmptyResults: true, testResults: '**/build/test-results/junit-platform/TEST-*.xml'
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
