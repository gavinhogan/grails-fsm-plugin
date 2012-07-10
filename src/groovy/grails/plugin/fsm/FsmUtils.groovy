package grails.plugin.fsm;

import groovy.lang.MetaProperty;
import org.codehaus.groovy.grails.commons.spring.DefaultBeanConfiguration;

import org.codehaus.groovy.runtime.metaclass.ThreadManagedMetaBeanProperty;

import grails.plugin.fsm.FsmSupport
import grails.plugin.fsm.FsmSupportException

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.metaclass.*


class FsmUtils {

    final static def FSMDEF="fsm_def"

    static def mockFsm(domainClass) {
        def log = new Expando(
        					trace: { println it },
        					debug: { println it },
        					info: { println it },
        					warn: { println it },
        					error: { println it }
        					)

    	MetaClassRegistry registry = GroovySystem.metaClassRegistry
        def fsm = GrailsClassUtils.getStaticPropertyValue(domainClass, FSMDEF )
        if (fsm) {
            // Will create the proper FsmSupport instance!
            fsm.each {String p, definition ->
                definition.each { start, defclosure ->
                	def mp = domainClass.metaClass.getMetaProperty(p)
                    if (!mp)
                        throw new FsmSupportException("Error in FSM definition: '${domainClass}' does not have '${p}' property to hold defined workflow status!")
                	def tmp = domainClass.newInstance()
                	if (tmp[p] != null)
                		log.error("Default value of '${domainClass}.${p}' will be overriden by FSM definition for that property. ")

                    // Modify the metaclass so new instances will have new behaviour!!
                	domainClass.metaClass.setProperty(p, start)
                    domainClass.metaClass.setProperty("_fsm${p}", null)  // internal, will hold FsmSupport instance
                    domainClass.metaClass.fire = fireClosure
                    domainClass.metaClass."fire_${p}" = fireClosure.curry(p)
                    domainClass.metaClass.fireable = fireableClosure
                    domainClass.metaClass."fireable_${p}" = fireableClosure.curry(p)

                }
            }
            // This code is a COPY of DomainClassGrailsPlugin.enhanceDomainClasses
            // because I cannot seem to be able to decorate it.
            // We just added the "${p}" initializing!
            domainClass.metaClass.constructor = {->
            	def bean = BeanUtils.instantiateClass(domainClass)
            	fsm.each { pp, defdef ->
            		defdef.each { startstart, clos ->
            			bean."${pp}" = startstart
            		}
            	}
            	bean
            }
            domainClass.metaClass.static.create = {->
            	def bean = BeanUtils.instantiateClass(domainClass)
            	fsm.each { pp, defdef ->
            		defdef.each { startstart, clos ->
            			bean."${pp}" = startstart
            		}
            	}
            }
        }
    }

    // Closure for the 'fire' methods to add to the domain classes
    static Closure fireClosure = {String flow, String event ->
        // Fix current delegate so inner closures will work
        def targetObject = delegate
        targetObject = initializeTargetObject(flow, event, targetObject)
        return targetObject."_fsm${flow}".fire(event)
    }

    static def initializeTargetObject(String flow, String event, Object targetObject) {
        def flowdef = GrailsClassUtils.getStaticPropertyValue(targetObject.class, FsmUtils.FSMDEF )
        if (flowdef[flow] == null)
            throw new FsmSupportException("Can't fire on flow '${flow}' which is not defined in '${targetObject.class}'")
        //
        // If flow has not been already initialized, will do it (we'll also
        // take the time to intialize the rest of the flows ;) )
        if (targetObject."_fsm${flow}" == null)
            flowdef.each { property, definition ->
                definition.each { initialState, flowClosure ->
                    if (targetObject."_fsm${property}"== null) {
                        def currentState = targetObject.getProperty(property)
                        targetObject["_fsm${property}"] = new FsmSupport(targetObject, property, initialState, currentState)
                        flowClosure(targetObject."_fsm${property}".record())
                    }
                }
            }
        return targetObject        
    }

    static Closure fireableClosure = { String flow, String event ->
        def targetObject = delegate
        targetObject = initializeTargetObject(flow, event, targetObject)
        return targetObject."_fsm${flow}".isFireable(event)
    }


}

