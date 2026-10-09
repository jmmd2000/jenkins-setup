FROM jenkins/jenkins:2.580.1-lts-jdk21

USER root

ADD --chmod=644 https://download.docker.com/linux/debian/gpg /etc/apt/keyrings/docker.asc
RUN echo "deb [signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian trixie stable" \
    > /etc/apt/sources.list.d/docker.list \
  && apt-get update \
  && apt-get install --yes --no-install-recommends \
    docker-ce-cli=5:29.9.0-1~debian.13~trixie \
    docker-buildx-plugin=0.38.0-1~debian.13~trixie \
  && rm -rf /var/lib/apt/lists/*

USER jenkins

COPY plugins-lock.txt /usr/share/jenkins/ref/plugins-lock.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins-lock.txt