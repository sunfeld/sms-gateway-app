FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --production

COPY server.js crypto.js ./

# Keys directory will be mounted as volume
RUN mkdir -p /app/keys

EXPOSE 3100

CMD ["node", "server.js"]
