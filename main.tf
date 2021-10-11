terraform {
  required_providers {
    heroku = {
      source  = "heroku/heroku"
      version = "~> 4.0"
    }
  }
}

variable "app_name" {
  description = "Name of the Heroku app provisioned as an example"
}

resource "heroku_app" "app" {
  name   = var.app_name
  region = "us"
}

resource "heroku_build" "app" {
  app        = heroku_app.app
  buildpacks = ["https://github.com/heroku/heroku-buildpack-clojure"]

}

# Launch the app's web process by scaling-up
resource "heroku_formation" "example" {
  app        = heroku_app.app.name
  type       = "web"
  quantity   = 1
  size       = "Standard-1x"
  depends_on = [heroku_build.app]
}

output "example_app_url" {
  value = "https://${heroku_app.app.name}.herokuapp.com"
}
