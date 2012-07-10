class Foo2 {
    static constraints = {
    }
    
    String estado = 'inicial'
    String estadoEnvio = 'envioInicial'
    static fsm_def = [
        estado : [
            inicial : { flow ->
                flow.on('comando') {
                    from('inicial').to('final')
                }
            }
        ],
        
        estadoEnvio : [
            envioInicial : { flow ->
                    flow.on('comandoEnvio') {
                        from('envioInicial').to('envioFinal')
                    }
            }
        ]
    ]
}
