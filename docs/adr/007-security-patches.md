# 007: Security patch override and broker principals

Accepted 2026-09-27. Trivy0.74.0 reported three critical Tomcat11.0.24 findings in Boot4.0.8's dependency set. Apache's
official security page lists further fixes in11.0.26. Maven Central confirms11.0.26 artifacts; override only
`tomcat.version`, retain Boot4.0.8 and its remaining dependency management. Reverify real Oracle/security tests and all
images. Remove the override when a future explicitly authorized Boot update includes the same or newer patched Tomcat.

Sources: https://tomcat.apache.org/security-11.html
and https://repo.maven.apache.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/11.0.26/ . Four application images
subsequently scan with zero HIGH/CRITICAL findings. This does not cover every severity or establish absence of unknown
vulnerabilities.

Kafka uses SASL/PLAIN with separate member/content/notification principals, explicit topic/group ACLs, and a separate
local administrative principal for provisioning/replay. Local SASL_PLAINTEXT is restricted to the dedicated private
container network and loopback; production must use TLS. Apps cannot administer topics. Oracle migration owners and
DML-only runtime users have separate per-service passwords. Dedicated migration processes exit before application
startup.
