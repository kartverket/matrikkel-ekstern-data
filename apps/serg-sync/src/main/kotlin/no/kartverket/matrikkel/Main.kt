package no.kartverket.matrikkel

private const val DISABLE_EXTERNAL_AUTHENTICATION =
    "--disable-external-authentication"

fun main(args: Array<String>) {
    runApplication(disableExternalAuthentication = DISABLE_EXTERNAL_AUTHENTICATION in args,)
}
