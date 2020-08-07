# Akka HTTP quickstart 

Opinionated fork of the Lightbend Akka HTTP g8 template.

## Usage 

Prerequisites:
- JDK 8+
- sbt 1.13.13+ 

In the console, type:

```sh
sbt -Dsbt.version=1.3.13 new umatrangolo/akka-http-quickstart-scala.g8
```

This template will prompt for the following parameters:

| Name | Description |
|------|-------------|
|name  | Becomes the name of the project |
|organization | The organization owning this app |
|package | Starting package |
|docker_maintainer| email of the maintainer of this app |
|docker_package_name| Specifies the package name for Docker | 

Once inside the project folder use the following command to run the code:

## Running

The minimal service will start out of the box with

```
sbt run
```

You can also build a Docker image with:

```
sbt docker:publishLocal
```

and run it with:


```
docker run --rm -P $package/$name:latest 
```

The service will listen on port 9000 with a basic healthcheck:

```
curl localhost:9000/health
```

## References

[g8](http://www.foundweekends.org/giter8/)
