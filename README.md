## Features


## Building


## Running

With maven
```
mvn compile
mvn exec:java
```

## Configuration properties

The following system properties can be set to control the behavior of the server: 

| Name                                          | Default           | Description                                |
|-----------------------------------------------|-------------------|--------------------------------------------|
| `com.nsantos.httpfileserver.base-path`        | ${java.io.tmpdir} | Base directory from where to serve files   |
| `com.nsantos.httpfileserver.thread-pool-size` | 8                 | Size of thread pool that handles requests  | 
| `com.nsantos.httpfileserver.port`             | 8081              | Listen port. If 0 random port              | 

Alternatively, the configuration can be defined in a configuration file in the format defined by 
[Lightbend configuration library](https://github.com/lightbend/config), for instance:

```
com.nsantos.httpfileserver {
  base-path = /var/lib/files
  thread-pool-size = 4
  port = 8080
}
```

And provided to the application by setting the system property `config.file`, for instance, by adding the argument
`-Dconfig.file=custom.conf`.


## Testing

### Unit testing
`mvn test`


### Integration testing

`mvn verify`



### Future work

- Use Guice for DI, instead of manually passing the dependencies in the constructor.
- Support range requests
- Consider using Java NIO SocketChannels and FileChannel to do zero-copy transfer from the file system cache to the socket.

