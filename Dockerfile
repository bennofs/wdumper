FROM openjdk:11

RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - && echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list
RUN apt-get update && apt-get -y install nodejs npm yarn


ADD . /src
RUN cd /src && ./gradlew install -x test # tests require db, which requires docker

ENTRYPOINT [ "/src/build/install/wdumper/bin/wdumper-cli" ]
