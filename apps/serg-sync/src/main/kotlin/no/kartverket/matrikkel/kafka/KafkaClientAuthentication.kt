package no.kartverket.matrikkel.config

import no.kartverket.heimdall.common.tokenclient.TokenClientFactory
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi
import no.kartverket.matrikkel.kafkaclient.ClientAuthentication

class KafkaClientAuthentication(
    val downstreamApi: DownstreamApi
): ClientAuthentication {

    override fun getAuthenticationHeaderValue(): String {
        return TokenClientFactory.MachineToMachine.azureAd()
            .createToken(downstreamApi).serialize()
    }
}