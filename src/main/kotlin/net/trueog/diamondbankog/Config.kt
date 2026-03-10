package net.trueog.diamondbankog

interface Config {
    var prefix: String
    var postgresUrl: String
    var postgresUser: String
    var postgresPassword: String
    var postgresTable: String
    var postgresLogTable: String
    var postgresLogLimit: Int
    var redisUrl: String
}
