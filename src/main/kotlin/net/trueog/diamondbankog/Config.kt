package net.trueog.diamondbankog

interface Config {
    val prefix: String
    val postgresUrl: String
    val postgresUser: String
    val postgresPassword: String
    val redisUrl: String
}
