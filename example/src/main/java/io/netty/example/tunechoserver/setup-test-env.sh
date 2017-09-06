:
sudo ip tuntap add dev tun-echo-server mode tun user $USER group $USER
sudo ip link set tun-echo-server up
sudo ip addr add 172.20.0.1/12 dev tun-echo-server
sudo ip addr add fd00:0:1::1/64 dev tun-echo-server
