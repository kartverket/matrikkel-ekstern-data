package no.kartverket.matrikkel.kafka

import no.kartverket.heimdall.common.tokenclient.client.BoundMachineToMachineTokenClient
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi
import no.kartverket.heimdall.common.tokenclient.client.MachineToMachineTokenClient
import no.kartverket.matrikkel.kafkaclient.ClientAuthentication

fun MachineToMachineTokenClient.asKafkaAuth(scope: String) = this.bindTo(scope).asKafkaAuth()
fun MachineToMachineTokenClient.asKafkaAuth(scope: DownstreamApi) = this.bindTo(scope).asKafkaAuth()
fun BoundMachineToMachineTokenClient.asKafkaAuth() = object : ClientAuthentication {
    override fun getAuthenticationHeaderValue(): String {
        val token = this@asKafkaAuth.createToken().serialize()
        return "Bearer $token"
    }
}