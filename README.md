# FileSync
FileSync is a Java Socket based application for keeping the files on multiple clients in sync. 
It contains a Server and a Client application. 
Multiple clients can connect with the server and their repositories will be maintained in sync automatically by this application.

This application currently uses simple java Socket and ServerSocket classes for communication. For each client a separate thread is being created on server, which is not a scalable design. If the number of clients increases too many threads will be created on server.
So an enhanced version of this application using NIO will be created, where number of threads being created will depend on the load, instead of numnber of connections.

Also currently this application is based on push mechanism, i.e. changes published by one client are pushed to other clients, instead of clients pulling them. This enhancement will also be done in newer version.
