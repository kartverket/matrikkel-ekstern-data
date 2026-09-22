package no.kartverket.matrikkel.config

import no.kartverket.matrikkel.kafkaclient.ClientAuthentication
import no.kartverket.oidc.tokenclient.TokenClientFactory
import no.kartverket.oidc.tokenclient.client.DownstreamApi

class KafkaClientAuthentication(
    val downstreamApi: DownstreamApi
): ClientAuthentication {

    override fun getAuthenticationHeaderValue(): String {
        return TokenClientFactory.createMachineToMachineTokenClient()
            .createMachineToMachineToken(downstreamApi).header.toString()
    }
}