
# reproducer for http4s `service cancelled response` issue 

http4s based service implementation and load tests for
reproducing issue with async cancellation. The issue
can be observed when the following message appears in the log:
`service cancelled response`

## running the tests

Start upstream service
```
sbt 'runMain foo.SlowServer'
```

Start main service
```
sbt 'runMain foo.MainServer'
```

Generate load
```
artillery run -o test-run-output.json http4s-load.yml
```