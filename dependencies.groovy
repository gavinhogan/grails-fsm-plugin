grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn" // log level of Ivy resolver, either 'error',
               // 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsHome()
        grailsCentral()
        mavenCentral()
        mavenRepo "http://snapshots.repository.codehaus.org"
        mavenRepo "http://repository.codehaus.org"
        mavenRepo "http://download.java.net/maven/2/"
        mavenRepo "http://repository.jboss.com/maven2/"
    }
    dependencies {
    }
    plugins{
        test ":code-coverage:1.2.5"
    }
}


