package net.trueog.diamondbankog.config

interface Config {
    val prefix: String
    val postgresHost: String
    val postgresPort: Int
    val postgresDatabase: String
    val postgresUser: String
    val postgresPassword: String?
    val redisHost: String
    val redisPort: Int
    val redisDatabase: Int
    val redisPassword: String?
}
