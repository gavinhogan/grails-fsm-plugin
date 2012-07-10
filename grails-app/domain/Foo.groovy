class Foo {

    def status

    static fsm_def = [
                status : [
                    loaded : { flow ->
                        flow.on ('ev1') {
                            from('loaded').when({hasAttrErrors()}).to('in_error')
                            from('loaded').when({!hasAttrErrors()}).to('validated')
                            }
                        flow.on('ev2') {
                            from('in_error').when({!hasAttrErrors()}).to('validated')
                        }
                        flow.on('ev3') {
                            from('validated').when({!hasAttrErrors()}).to('done')
                        }
                    }
                ]]
}
