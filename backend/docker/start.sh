#!/bin/sh

set -xe

yarn prisma migrate deploy
exec node server.js
