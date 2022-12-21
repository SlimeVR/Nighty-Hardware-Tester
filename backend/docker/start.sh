#!/bin/sh

set -xe

yarn prisma migrate deploy
node server.js
