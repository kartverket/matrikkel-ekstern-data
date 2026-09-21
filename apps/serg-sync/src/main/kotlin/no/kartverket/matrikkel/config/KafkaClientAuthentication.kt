package no.kartverket.matrikkel.config

import no.kartverket.matrikkel.kafkaclient.ClientAuthentication
import no.kartverket.oidc.tokenclient.TokenClientFactory
import no.kartverket.oidc.tokenclient.client.DownstreamApi

object KafkaClientAuthentication: ClientAuthentication {

    override fun getAuthenticationHeaderValue(): String {
        return TokenClientFactory.createMachineToMachineTokenClient()
            .createMachineToMachineToken(
            DownstreamApi(
                cluster = "",
                namespace = "",
                application = ""
            )).header.toString()
    }
}