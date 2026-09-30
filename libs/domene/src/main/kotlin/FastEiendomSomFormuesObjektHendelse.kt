data class FastEiendomSomFormuesObjektHendelse(
    val matrikkelIdent: Long,
    val kommuneIdent: String,
    val skatteregistrerteEiere: Set<String>, // TODO sette opp mapper lag
)