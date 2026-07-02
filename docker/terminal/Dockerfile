FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        coreutils \
        findutils \
        gawk \
        grep \
        nano \
        procps \
        sed \
        tree \
        util-linux \
        vim \
    && useradd --create-home --shell /bin/bash suser \
    && printf '%s\n' 'export PS1="suser@linux-terminal:\w\$ "' >> /home/suser/.bashrc \
    && printf '%s\n' 'if [ -f ~/.bashrc ]; then . ~/.bashrc; fi' > /home/suser/.bash_profile \
    && chown suser:suser /home/suser/.bashrc /home/suser/.bash_profile \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

USER suser
WORKDIR /home/suser

CMD ["/bin/bash"]
