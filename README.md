# SIMEX Authentication Orchestrator
An authentication service for Event Driven service using SIMEX messaging API.

## Overview
It receives a SIMEX message on RabbitMQ and does the following:

1. If the request method is `SELECT`, then this is a request for either authentication, or for a renewal of authorization token.
2. If the request method is `RESPONSE`, then this is a response from a database service returning the user principal.

The results of both are stored in a caching system, such as Hazelcast by:
1. Authorization JWT Token: Stores the User Principal, i.e. the authenticated user information.
2. Refresh JWT Token: Stores the User Principal, i.e. the authenticated user information.

In practise, the authorization token cache has a small TTL, such as 5 minutes, and the token itself has a 5-minute expiration.
This cache can be made available to all other services so that they can validate the token without interacting with this service.

The refresh token cache is usually a bit longer, 24 hours is standard, and the refresh token itself has a 24-hour expiration.
This system uses a refresh token rotation scheme, in that each time the refresh token is used to generate another authorization
token, the refresh token is re-generated as well. The refresh token cache should not be available to other services, thus forcing 
the client to send a message to this service to re-generate authorization and refresh token.

## Authentication and JWT Token Generation
### Step 1 - Customer Authentication/Authorization
It receives a SIMEX message, generally from `drop-off-service`, for a customer authentication:
```json
{
  "endpoint": {
    "resource": "service.auth",
    "method": "select",
    "entity": "authentication"
  },
  "client": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "authorization": ""
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "originalToken": ""
  },
  "data": [
    {
      "field": "username",
      "value": "user@test.com"
    },
    {
      "field": "password",
      "value": "password1234"
    }
  ]
}
```
In order to improve security, although it probably won't make much difference, the `authorization` could have a default access token to begin with 
or left blank as above.

### Step 2 - Send request to data store for user information
The service will remove the password from `data` and, after updating the `endpoint` and `client`, send it to data store, in this case `service.dbread`:

```json
{
  "endpoint": {
    "resource": "service.dbread",
    "method": "select",
    "entity": "user"
  },
  "client": {
    "clientId": "service.auth",
    "requestId": "app-1-app1-r-1",
    "sourceEndpoint": "service.auth",
    "authorization": "some internal security key"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "originalToken": ""
  },
  "data": [
    {
      "field": "username",
      "value": "user@test.com",
      "operator": "EQ"
    }
  ]
}
```
It will save the original message in the request cache, using `s"${msg.originator.clientId}-${msg.originator.requestId}"` as the key.

### Step 3 - Receives a response from the Database service
The database service will search for the customer and return a response:

```json
{
  "endpoint": {
    "resource": "service.auth",
    "method": "response"
  },
  "client": {
    "clientId": "service.dbread",
    "requestId": "app-1-app1-r-1",
    "sourceEndpoint": "service.dbread",
    "authorization": "some internal security key"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "originalToken": ""
  },
  "data": [
    {
      "field": "customerId",
      "value": "1"
    },
    {
      "field": "firstname",
      "value": "John"
    },
    {
      "field": "surname",
      "value": "Smith"
    },
    {
      "field": "email",
      "value": "user@test.com"
    },
    {
      "field": "password",
      "value": "==aeacls1ktyaysb"
    }
  ]
}
```
If no matching customer is found, the `data` array will be empty. Notice the `endpoint.method` is `response`, as this is a
response to a request. There is no need for any further information, such as `Not Found` as an empty array implies it.


### Step 4 - Validates the customer and sends the response to Collection Point
It will check the password for matching and sends the response to the collection point service:
```json
{
  "endpoint": {
    "resource": "service.collection",
    "method": "response",
    "entity": "authorization"
  },
  "client": {
    "clientId": "service.auth",
    "requestId": "app-1-app1-r-1",
    "sourceEndpoint": "service.auth",
    "authorization": "some internal security key"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "originalToken": ""
  },
  "data": [
    {
      "field": "customerId",
      "value": "1"
    },
    {
      "field": "firstname",
      "value": "John"
    },
    {
      "field": "surname",
      "value": "Smith"
    },
    {
      "field": "authorisation",
      "value": "some_auth_JWT_token"
    },
    {
      "field": "refresh_token",
      "value": "some_refresh_JWT_token"
    }
  ]
}
```
This message is stored in both the authorization cache and in the refresh token cache.

If either the username was not found or the password did not match, the following response can be sent:
```json
{
  "endpoint": {
    "resource": "service.collection",
    "method": "response",
    "entity": "authorization"
  },
  "client": {
    "clientId": "service.auth",
    "requestId": "app-1-app1-r-1",
    "sourceEndpoint": "service.auth",
    "authorization": "some internal security key"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-authentication",
    "originalToken": ""
  },
  "data": [
    {
      "field": "status",
      "value": "authentication failed"
    },
    {
      "field": "message",
      "value": "Either the username or the password did not match"
    }
  ]
}
```

## Refresh token
The client will send the following message to this service:

```json
{
  "endpoint": {
    "resource": "service.auth",
    "method": "select",
    "entity": "refresh"
  },
  "client": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-refresh-token-service",
    "authorization": "current authorization token"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-refresh-token-service",
    "originalToken": "current authorization token"
  },
  "data": [
    {
      "field": "refresh_token",
      "value": "refresh JWT Token"
    }
  ]
}
```

The service will check if the refresh token is valid by checking the refresh cache.
If it is, then the system will generate new authorization token and a refresh token and replace the existing ones in cache and send
the same message as in **step 4** above, replacing the tokens.

If the refresh token was not found, then it will send the following message:

```json
{
  "endpoint": {
    "resource": "service.collection",
    "method": "response",
    "entity": "refresh"
  },
  "client": {
    "clientId": "service.auth",
    "requestId": "app-1-app1-r-1",
    "sourceEndpoint": "service.auth",
    "authorization": "some internal security key"
  },
  "originator": {
    "clientId": "app-1",
    "requestId": "app1-r-1",
    "sourceEndpoint": "app-refresh-token-service",
    "originalToken": "current authorization token"
  },
  "data": [
    {
      "field": "status",
      "value": "Not Found"
    },
    {
      "field": "message",
      "value": "The requested data was not found"
    }
  ]
}
```


## Dockerising and Running Docker Image
This project has sbt-native-packager enabled for Docker images. Use:

```
    sbt docker:publishLocal
```
which will install a docker image locally. You can then start it locally exposing port 8002.
It will be automatically tagged with the build version.

To push the image into Docker hub:
```
    docker login
    docker push <repo>/authentication-orchestrator:<version>
```

To run the docker image against services running from `docker-compose` using `drop-off-service`:

`docker run -h auth-service --name auth-service --net shareprice-service_internal -p 127.0.0.1:8003:8003/tcp  ramindur/authentication-orchestrator:<version>`

