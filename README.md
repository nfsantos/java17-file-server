# HTTP File Server

This project implements a simple HTTP Server for serving files from a local directory.

Key features:

- HTTP Keep-alive - Clients can send multiple HTTP requests in the same TCP connection.
- Directory listing - Requests for directories produce an HTML listing which can be used to navigate, similar to the 
  static serving of nginx or of the Python HTTP server module (`pyhon -m http.server`).
- Concurrent requests - Can serve multiple concurrent connections, using a thread-per-connection model. 

It is implemented in Java 17, making use of several recent features of the language, like records, local type inference,
and pattern matching for instancof.

The web server is implemented from scratch, using the Java Sockets API and the Java Executors service 
(java.util.concurrent).

The tests use the Java HTTP Client (java.net.http).

## Running

You need Java 17 in the path. The `dist` sub-directory contains a compiled version (as a fat-jar) and can be run with 
the following command:  

```
$ ./run-dist.sh /var/lib/files
```

This would start an instance of the web server, serving files from `/var/lib/files`.

Alternatively, the script `./run-from-maven.sh <dir>` uses maven to compile and run directly from the class files generated
during compilation (in essence, it runs the `mvn exec:java` after processing the command line argument).

## Building and packaging

To build you can use maven directly, with `mvn compile`. The script `./make-dist.sh` will compile, package and place the 
generated jar in `dist`, where it can be run with `./run-dist.sh`

## Configuration properties

The following system properties can be set to control the behavior of the server:

| Name                                         | Default           | Description                                |
|----------------------------------------------|-------------------|--------------------------------------------|
| `com.nsantos.httpfileserver.base-path`       | ${java.io.tmpdir} | Base directory from where to serve files   |
| `com.nsantos.httpfileserver.thread-pool-size` | 8                 | Size of thread pool that handles requests  | 
| `com.nsantos.httpfileserver.port`            | 8081              | Listen port. If 0 random port              | 
| `com.nsantos.httpfileserver.keep-alive-timeout` | 30 seconds    | How long to keep an idle connection alive  |  

Alternatively, the configuration can be defined in a configuration file in the format defined by
[Lightbend configuration library](https://github.com/lightbend/config), for instance:

```
com.nsantos.httpfileserver {
  base-path = /var/lib/files
  thread-pool-size = 4
  port = 8080
  keep-alive-timeout = 30 seconds
}
```

And provided to the application by setting the system property `config.file`, for instance, by adding the argument
`-Dconfig.file=custom.conf`.

## Testing

The tests can be run with `mvn test`.


## Future work

There are several possible ways of improving this project.  

Additional functionality:
- Support HTTP range requests. This is especially useful to download large files, as it allows resuming interrupted downloads.
- Return 503 when there are no threads available to server the request. The current implementation will accept the 
  connection request but won't start reading requests until one of the threads becomes available. The client will wait
  a long time until the request is finally processed or until it timeouts, which is not a good user experience.

Improvements to non-functional requirements:
- Use Java NIO SocketChannels and FileChannel API. This allows doing zero-copy transfers from the file system cache to
  the socket, which can improve performance and/or reduce load on CPU.
- For highly concurrent servers, use non-blocking I/O (Java NIO Selector API) instead of the thread-by-connection model.
  This can greatly reduce the memory and CPU requirements, as well as increase the maximum number of concurrent
  connections supported.

Improvements to the code base:
- Add tests for keep-alive. I have verified manually that it is working, but did not implement automated tests. 
  For testing I have used the HTTP  
- Use Guice for DI, instead of manually passing the dependencies in the constructor.
- Implement load and stress tests. This could be done with an external library like JMeter or Gatling.

