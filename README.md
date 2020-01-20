# Bifrost - Elasticsearch subscriptions service

## Http GET
**Json formatted log lines**

Path: /

Query parameters:

- matchFilters: comma separated key:value pairs used for exact matching
- prefixFilters: comma separated key:value pairs used for prefix matching 
- afterLine: urlencoded json serialized Line object having at least the timestamp and id fields
- direction: ASC|DESC
- maxLines: integer of max returned lines

\* key:value query: *key* is a field name, *value* is a matching string, for 'OR' matching  multiple values can be defined with '|'   

**Plain text log lines**

Path: /text

Query parameters

- matchFilters: comma separated key:value pairs used for exact matching
- prefixFilters: comma separated key:value pairs used for prefix matching 
- afterLine: urlencoded json serialized Line object having at least the timestamp and id fields
- direction: ASC|DESC
- maxLines: integer of max returned lines

- follow: true|false keep the connection open for getting new lines
- timeoutProbeString: when a string is defined the server is sending given string as a connection probe, the string is printed within the logs

\* key:value query: *key* is a field name, *value* is a matching string, for 'OR' matching  multiple values can be defined with '|'   

**Log messages checksum**

Path: /metadata

Same query parameters as for the *Json formatted log lines*.


**Examples query**

```
curl "http://host.com/text/\
?maxLines=1000\
&matchFilters=mdc.processContext.keyword:build-448,level.keyword:INFO\
&prefixFilters=loggerName.keyword:org.jboss.pnc._userlog_.build-log"

#response
{
  "logger" : "org.jboss.pnc._userlog_.build-log",
  "last" : false,
  "id" : "log#AW0_yHCckFSN2lTD2LUF",
  "timestamp" : "2019-09-17T15:12:43.495Z",
  "message" : "Quick brown fox jumps over the lazy dog."
}
```

## Websocket

Socket path: /socket

**Get over websocket**

To get the lines over websocket, send the "request" message to the websocket:
```
{
  "method":"GET-LINES",
  "id":1,
  "params":
  {
    "maxLines":"10",
    "matchFilters":"",
    "prefixFilters":"",
    "afterLine":null,
    "direction":"ASC",
    "class":"class org.jboss.pnc.bifrost.endpoint.websocket.GetLinesDto"
  }
  ,"jsonrpc":"2.0"
}
```

**Subscribe to new log lines**

To subscribe to new lines over websocket, send the "request" message to the websocket, lines are send as WebSocket Binary message
```
{
  "method":"SUBSCRIBE",
  "id":2,
  "params":
  {
    "matchFilters":"",
    "prefixFilters":"",
    "class":"class org.jboss.pnc.bifrost.endpoint.websocket.SubscribeDto"
  }
  ,"jsonrpc":"2.0"
}
```

\* matchFilter and prefixFilter follow the same pattern as in http get requests described above

**Unsubscribe**

To subscribe to new lines over websocket, send the "request" message to the websocket, lines are send as WebSocket Binary message
```
{
  "method":"UNSUBSCRIBE",
  "id":3,
  "params":
  {
    "subscriptionTopic":"",
    "class":"class org.jboss.pnc.bifrost.endpoint.websocket.UnSubscribeDto"
  }
  ,"jsonrpc":"2.0"
}
```
\* a subscription topic is returned from the subscribe action



