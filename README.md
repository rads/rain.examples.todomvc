# rain.examples.todomvc

**Live example: [https://todomvc.rads.dev/](https://todomvc.rads.dev)**

This is an example full-stack app built with [Rain](https://github.com/rads/rain), allowing for server-side rendering in a Reagent/Re-frame application.

## Getting Started

Start the dev server:

```shell
bb dev
```

## Features

- Everything runs in a single JVM process
- Can be deployed to a generic Linux machine (currently using Ubuntu 22.04 on [Hetzner Cloud](https://www.hetzner.com/cloud))
- Uses [Biff](https://biffweb.com) for overall architecture and deployment
- Uses [Babashka](https://github.com/babashka/babashka) for running project tasks with minimal startup time
- Uses [Ring](https://github.com/ring-clojure/ring), [Reitit](https://github.com/metosin/reitit), and [Jetty](https://github.com/sunng87/ring-jetty9-adapter) on the backend to implement the HTTP server
- Uses [Reagent](https://github.com/reagent-project/reagent) and [Re-frame](https://github.com/day8/re-frame/) for state management
- Uses [Rain](https://github.com/rads/rain) to render Reagent and Re-frame components on the server _without a Node.js runtime_
- Todos are persisted to Postgres using the existing [todo-backend-reitit](https://github.com/prestancedesign/todo-backend-reitit) project as a library (props to [@prestancedesign](https://github.com/prestancedesign/todo-backend-reitit))
- A Swagger UI for the backend is available at [https://todomvc.rads.dev/swagger-ui](https://todomvc.rads.dev/swagger-ui)
- The initial Re-frame DB is serialized into a `<script>` tag on the server and hydrated with [`hydrateRoot`](https://react.dev/reference/react-dom/client/hydrateRoot) on the client
- If JavaScript is disabled, the site can still be used in read-only mode
- All UI updates are optimistic (no interaction delay even on "Slow 3G")
- SSR allows the page to load all content quickly while JavaScript is being loaded in the background, optimizing for First Content Paint (FCP)
