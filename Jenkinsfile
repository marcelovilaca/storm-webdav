#!/usr/bin/env groovy
@Library('sd')_
def kubeLabel = getKubeLabel()

pipeline {

  agent {
    kubernetes {
      label "${kubeLabel}"
      cloud 'Kube mwdevel'
      defaultContainer 'runner'
      inheritFrom 'ci-template'
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timeout(time: 2, unit: 'HOURS')
  }
    
  triggers { cron('@daily') }

  stages {
    stage('build') {
      steps {
        sh 'mvn -B clean compile'
      }
    }
    
    stage('test') {
      steps {
        sh 'mvn -B clean test'
      }
      post {
        always {
          junit '**/target/surefire-reports/TEST-*.xml'
        }
      }
    }
    
    stage('PR analysis') {
      when {
        not {
          environment name: 'CHANGE_URL', value: ''
        }
      }
      steps {
        script {
          def tokens = "${env.CHANGE_URL}".tokenize('/')
          def organization = tokens[tokens.size()-4]
          def repo = tokens[tokens.size()-3]

          withCredentials([string(credentialsId: '630f8e6c-0d31-4f96-8d82-a1ef536ef059', variable: 'GITHUB_ACCESS_TOKEN')]) {
            withSonarQubeEnv{
              sh """
                mvn -B -U clean compile sonar:sonar \\
                  -Dsonar.analysis.mode=preview \\
                  -Dsonar.github.pullRequest=${env.CHANGE_ID} \\
                  -Dsonar.github.repository=${organization}/${repo} \\
                  -Dsonar.github.oauth=${GITHUB_ACCESS_TOKEN} \\
                  -Dsonar.host.url=${SONAR_HOST_URL} \\
                  -Dsonar.login=${SONAR_AUTH_TOKEN}
              """
            }
          }
        }
      }
    }

    stage('analysis') {
      when {
        anyOf { branch 'master'; branch 'develop' }
        environment name: 'CHANGE_URL', value: ''
      }
      steps {
        script {
          def opts = '-Dmaven.test.failure.ignore -DfailIfNoTests=false'
          def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

          withSonarQubeEnv{
            sh "mvn clean compile -U ${opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}"
          }
        }
      }
    }
    
    stage('package') {
      steps {
        sh 'mvn -B -DskipTests=true clean package'
      }
    }
    
    stage('result') {
      steps {
        script { currentBuild.result = 'SUCCESS' }
      }
    }
  }
    
  post {
    failure {
     slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    unstable {
      slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
    }
    changed {
      script{
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }  
}
