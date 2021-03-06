credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")

addSbtPlugin("com.lightbend.rp" % "sbt-reactive-app"  % "1.5.0")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.9.3")

resolvers += Resolver.url("lightbend-commercial", url("https://repo.lightbend.com/commercial-releases"))(Resolver.ivyStylePatterns)
