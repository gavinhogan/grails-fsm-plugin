package grails.plugin.fsm;
/**
 *
 * DSL for defining Finite State Machines.
 *
 * Derived work of:
 *   http://www.bytemycode.com/snippets/snippet/640/
 *
 * New Features:
 *   - It now will allow 'when' clauses, acepting a closure whose evaluation
 *     to true/false will drive the transition being usable or not.
 *   - Logically, it nows accept multiple transitions from the same state/event.
 *   - It will get a target object in the constructor, and will provide this object
 *     with 'firexxxx' methods so the FSM behaviour is implemented in the entity
 *     (this has now been moved into the Grails plugin, so no longer in this class)
 *
 * See FsmSupportTests.groovy for use samples.
 *
 * TODO: Add 'DO' clause so some code might be launched when a transition is triggered
 * TODO: Add 'wildcards (*)' to 'from' so we can generalize better!
 * TODO: Allow an option to allow "wildcards" or not?
 * TODO: Define a way of unifying the event models, so one event can move several
 * flows defined in the entity
 * TODO: Why DON'T fireXXXX methods work with Grails' entities? Forced to use fire(flow, event)!!
 * @author jorge
 *
 */
class FsmSupport
{
    /*
     * A map or maps to store events and from states of each
     * transition. The last element is another map with 'to' states
     * and its attached condition, to be evaluated
     *
     *
     *
        transitions = [
            event1 : [
                from1 : [
                    to1 : cond1,  // "conds" are closures!!
                    to2 : cond2,  // order is not guaranteed
                    to3 : cond3   // first to be evaluated 'true' will win ;)
                ],
                from2 : [
                    to1 : cond1,
                    to2 : cond2
                ]
                from3 : [
                    to1 : {true}  // default closure condition 'true'
                ]
            ]
        ]
     */
    def transitions = [:]
    def actions = [:]

    def initialState
    def currentState
    def target
    def property

    /**
     * Will hold relationships to any object & property any FsmSupport
     * has been tied to.
     */
    static relationships = [:]

    def FsmSupport(targetObject, targetProperty, a_initialState = null, a_currentState = null)
    {
        /**
         * If no initialState has been provided we'll try to get it
         * from the object-property itself!!
         */
        if (!a_initialState)
            a_initialState = targetObject.getProperty(targetProperty)
        initialState = a_initialState
        if (a_currentState)
            currentState = a_currentState
        else
            currentState = initialState

        target = targetObject
        property = targetProperty
        def prop = property.toLowerCase()


        if (!relationships[targetObject])
            relationships[targetObject] = [:]
        relationships[targetObject][prop] = this

        targetObject[property] = currentState

    }

    def record()
    {
        Grammar.newInstance(this);
    }

    def registerTransition(a_grammar)
    {
        assert a_grammar.isValid(), "Invalid transition (${a_grammar})"

        def transition

        def event = a_grammar.event
        def fromState = a_grammar.fromState
        def toState = a_grammar.toState

        if (!transitions[event])
            transitions[event] = [:]

        transition = transitions[event]

// TODO: How to assert non-duplications now that they are conditioned?
//        assert cond == null && !transition[fromState], "Duplicate fromState ${fromState} for transition ${a_grammar}"

        def cond = a_grammar.cond!=null?a_grammar.cond:{true}
        def conditionedStates = transition[fromState] != null?transition[fromState] : [:]
        conditionedStates[toState] = cond
        transition[fromState] = conditionedStates
    }

    def addActionToTransition(a_grammar)
    {
        def fromState = a_grammar.fromState
        def toState = a_grammar.toState
        def key = "${a_grammar.event}-${fromState}"
        if (!actions[key])
        	actions[key] = [:]
        actions[key][toState] = a_grammar.action
    }

    def reset()
    {
        currentState = initialState
    }

    def fire(a_event)
    {
       assert currentState, "Invalid current state '${currentState}': pass into constructor"
        assert transitions.containsKey(a_event), "Invalid event '${a_event}', should be one of ${transitions.keySet()}"

        def fromState = currentState
        def transition = transitions[a_event]
        def nextState
        transition[currentState].each { to, Closure cond ->
            cond.setDelegate(target) // CUIDADO
            if ( !nextState && cond() ) {
                nextState = to
                // Notify the object if it's interested in the transition event
                if (target.metaClass.respondsTo(target, "onFsmTransition"))
                        target.onFsmTransition(fromState,to)
            }
        }
        assert nextState, "There is no transition from '${currentState}' to any other state. \nFSM: ${this.transitions}"
        currentState = nextState

        // Update the target property of the target object
        target[property] = currentState

        // Update the object if contains "save"
        if (target.metaClass.respondsTo(target, "save")) {
            target.save()
        }

        // Trigger the action defined if any
        def key = "${a_event}-${fromState}"
        if (actions[key] && actions[key][currentState]) {
        	def act = actions[key][currentState]
            act.setDelegate(target)
            act()
        }


        currentState
    }

    def isFireable(a_event) {
        def transition = transitions[a_event]
        return (transition[currentState]!=null)
    }

    def isState(a_state)
    {
        currentState == a_state
    }
}

class Grammar
{
    def fsm

    def event
    def fromState
    def toState
    def cond
    def action
    def conditionable  // 'from' != null && 'to' == null

    Grammar(a_fsm)
    {
        fsm = a_fsm
    }

    def on(a_event)
    {
        event = a_event
        toState = fromState = null
        this
    }

    def on(a_event, a_transitioner)
    {
        on(a_event)

        a_transitioner.delegate = this
        a_transitioner.call()

        this
    }

    def from(a_fromState)
    {
        conditionable = true
        cond = null
        fromState = a_fromState
        this
    }

    def to(a_toState)
    {
        assert a_toState, "Invalid toState: ${a_toState}"

        conditionable = false
        toState = a_toState
        fsm.registerTransition(this)
        this
    }
    def crash()
    {
        // TODO: Will allow to explicitely crash in a forbidden event!
        // This would imply changing default behaviour so non-declared events
        // not fail and just keep currentState.
        // TODO: Think about it...
    }

    /*
     * *Must* be called after 'from' and before 'to'
     */
    def when(cond_closure)
    {
        assert conditionable, "'when' must be called after a 'from' and before 'to'"
        cond = cond_closure
        this
    }

    def act(do_closure)
	{
    	assert toState, "'act' can only be called after a 'to' has been defined"
    	action = do_closure
    	fsm.addActionToTransition(this)
    	this
	}

    def isValid()
    {
        event && fromState && toState
    }

    public String toString()
    {
        "${event}:${fromState}=>${toState}"
    }
}

class FsmSupportException extends Exception {
    def FsmSupportException(String message) {
        super(message)
    }
}

