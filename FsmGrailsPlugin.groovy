import groovy.lang.MetaProperty;
import org.codehaus.groovy.grails.commons.spring.DefaultBeanConfiguration;

import org.codehaus.groovy.runtime.metaclass.ThreadManagedMetaBeanProperty;

import grails.plugin.fsm.FsmSupport
import grails.plugin.fsm.FsmUtils
import grails.plugin.fsm.FsmSupportException

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;


import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.metaclass.*


class FsmGrailsPlugin {
    // the plugin version
    def version = "0.6.6"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1.1 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    def loadAfter = ['controllers', 'hibernate', 'domainclass']
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/FsmSupportDummy.groovy",
            "grails-app/domain/FsmMultipleActions.groovy"
    ]
    def watchedResources = "file:./grails-app/domain/**/*.groovy"

    def author = "Jorge Uriarte"
    def authorEmail = "jorge.uriarte@omelas.net"
    def title = "Finite State Machine behaviour for domain classes"
    def description = '''\\
        This plugin allow definition of simple workflows attached to domain classes, including
        states, events, transitions and conditions.
        Current workflow's state will be held in domain class' property that must be defined.
        Multiple workflows can be defined on every domain class.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/fsm"

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)Classes
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional)
    }

    def doWithDynamicMethods = { ctx ->

        // Will add the fire closure where needed
        application.domainClasses.each {domainClass ->
            MetaClassRegistry registry = GroovySystem.metaClassRegistry
            def fsm = GrailsClassUtils.getStaticPropertyValue(domainClass.clazz, FsmUtils.FSMDEF )
            if (fsm) {
                // Will create the proper FsmSupport instance!
                fsm.each {String p, definition ->
                    definition.each { start, defclosure ->
                    	def mp = domainClass.metaClass.getMetaProperty(p)
                        if (!mp)
                            throw new FsmSupportException("Error in FSM definition: '${domainClass.clazz}' does not have '${p}' property to hold defined workflow status!")
                    	def tmp = domainClass.clazz.newInstance()
                    	if (tmp[p] != null)
                    		log.warn("Default value of '${domainClass.clazz}.${p}' will be overriden by FSM definition for that property. ")

                        // Modify the metaclass so new instances will have new behaviour!!
                        domainClass.metaClass.setProperty("_fsm${p}", null)  // internal, will hold FsmSupport instance
                        domainClass.metaClass.fire = FsmUtils.fireClosure
                        domainClass.metaClass."fire_${p}" = FsmUtils.fireClosure.curry(p)
                        domainClass.metaClass.fireable = FsmUtils.fireableClosure
                        domainClass.metaClass."fireable_${p}" = FsmUtils.fireableClosure.curry(p)
                    }
                }
                // This code is a COPY of DomainClassGrailsPlugin.enhanceDomainClasses
                // because I cannot seem to be able to decorate it.
                // We just added the "${p}" initializing!
                domainClass.metaClass.constructor = {->
	            	def bean
	                if(ctx.containsBean(domainClass.fullName)) {
	                    bean = ctx.getBean(domainClass.fullName)
	                }
	                else {
	                    bean = BeanUtils.instantiateClass(domainClass.clazz)
	                }
	            	fsm.each { pp, defdef ->
	            		defdef.each { startstart, clos ->
//	            			def setter = GrailsClassUtils.getSetterName(pp)
//	            			bean."${setter}"(startstart)
	            			bean."${pp}" = startstart
	            		}
	            	}
	            	bean
	            }
                domainClass.metaClass.static.create = {->
                	def bean = ctx.getBean(domainClass.getFullName())
	            	fsm.each { pp, defdef ->
	            		defdef.each { startstart, clos ->
	            			bean."${pp}" = startstart
	            		}
	            	}
                	bean
                }
            }
        }
    }

    def onChange = { event ->
    	event.manager?.getGrailsPlugin("fsm")?.doWithDynamicMethods(event.ctx)
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}

