# File format

A collection is a folder. Everything is YAML, one concept per file.

```
my-api/
  openrest.yaml            collection: name, auth, headers, variables, scripts, tests, docs
  environments/
    local.yaml             one file per environment
  workflows/
    Adopt every pet.yaml   one file per workflow
  .env                     optional: values for {{process.env.NAME}}
  Ping.yaml                a request
  Users/
    folder.yaml            folder-level headers, auth, variables, scripts
    Get user.yaml
```

Names containing characters that are not safe in file names are sanitised. `environments` and `workflows` are reserved folder names. Request order inside a folder is kept in each request's `seq` field.

## A request

```yaml
name: Get user
seq: 1
method: GET
url: '{{baseUrl}}/users/:id?verbose=true'
params:
  query: [{name: verbose, value: 'true'}]
  path:  [{name: id, value: '7'}]
headers:
- {name: Accept, value: application/json}
body:
  type: json            # none | json | text | xml | graphql | formUrlEncoded | multipartForm | file
  json: |-
    {"name": "Ada"}
auth: {type: bearer, token: '{{token}}'}   # omit to inherit from the folder or collection
vars:  {pre: [], post: [{name: id, value: res.body.id}]}
assertions: [{name: res.status, value: eq 200}]
scripts: {pre: "req.setHeader('X-A', '1')"}
tests: |-
  test('ok', () => expect(res.status).to.equal(200));
```

## An environment

```yaml
name: Local
variables:
- {name: baseUrl, value: 'http://localhost:8080'}
- {name: token, secret: true}      # no value in the file: it lives in ~/.openrest/secrets.json
```

## A workflow

See the full example in [Workflows](workflows.md#example). Steps reference requests by file name and can nest through `then`, `else` and `do`.

## Tips

- The files are meant to be read and reviewed. Keep them in git.
- Edit them outside OpenRest if you like: the app reloads from disk when you switch back to its window.
