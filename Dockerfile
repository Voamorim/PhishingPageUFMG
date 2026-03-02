FROM selenium/standalone-firefox:135.0 as selenium

USER root
ENV HOME /root
ENV WEB_PHISHING_CONFIG_FILE $HOME/environment/config.json
ENV WEB_PHISHING_JAR $HOME/workdir/WebPhishingFramework.jar

RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean;

RUN mkdir -p $HOME/workdir
WORKDIR $HOME/workdir

COPY target/WebPhishingFramework.jar $HOME/workdir/
COPY target/lib $HOME/workdir/lib

ENTRYPOINT ["java", "-jar", "/root/workdir/WebPhishingFramework.jar", "/root/environment/config.json"]