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
    && useradd --create-home --shell /bin/bash student \
    && printf '%s\n' 'export PS1="student@linux-terminal:\w\$ "' >> /home/student/.bashrc \
    && printf '%s\n' 'if [ -f ~/.bashrc ]; then . ~/.bashrc; fi' > /home/student/.bash_profile \
    && chown student:student /home/student/.bashrc /home/student/.bash_profile \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

USER student
WORKDIR /home/student

CMD ["/bin/bash"]
