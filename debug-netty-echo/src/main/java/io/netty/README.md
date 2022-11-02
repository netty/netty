### 编程步骤

1 创建 ServerSocketChannel 通道，绑定监听端口

2 设置通道是非阻塞模式

3 创建 Selector 选择器

4 把 Channel 注册到 Selector 选择器上，监听连接事件

5 调用 Selector 的 select() 方法（循环调用），检测通道的就绪情况

6 调用 selectKeys() 方法获取就绪 Channle 集合

7 遍历就绪 Channel 结合，判断就绪事件类型，实现具体的业务操作

8 根据业务，是否需要再次注册监听事件，重复执行

