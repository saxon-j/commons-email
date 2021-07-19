
Apache Commons Email 源码见 [Github](https://github.com/apache/commons-email).

源码看类： javax.activation.URLDataSource
```java
    public InputStream getInputStream() throws IOException {
        return this.url.openStream();
    }
 
    public String getContentType() {
         String type = null;
 
         try {
             if (this.url_conn == null) {
                 this.url_conn = this.url.openConnection();
             }
         } catch (IOException var3) {
         }
 
         if (this.url_conn != null) {
             type = this.url_conn.getContentType();
         }
 
         if (type == null) {
             type = "application/octet-stream";
         }
 
         return type;
     }
    
```
可以看到这里url_conn字段是被 getContentType() 与 getOutputStream() 两个方法做了复用

但是在 getInputStream() 中并没有复用url_conn，而是直接重新创建了个链接，拿到了一个新的流对象

===================

为方便大家理解，将整个流程简化为3个步骤

步骤1:
在添加附件时ApacheEmail会创建一个DataSource添加到Email对象中，如果附件是网络文件，会创建URLDataSource对象 ，此处会进行文件有效性校验，打开连接并关闭（连接1）


步骤2 ：
在发送邮件时首先会保存修改，然后再发送邮件
保存邮件时会通过DataHandler调用DataSource的 getContentType() 方法获取附件类型
如果是URLDataSource则会通过创建HttpURLConnection的方式获取到真实的附件类型，其中如果已经打开过连接的会直接复用，如果是新创建的连接会赋值给url_conn来进行复用（连接2）


步骤3:
发送邮件时，会调用DataHandler的writeTo()方法对数据进行写入，而方法内部则是调用DataSource的getInputStream()获取输入流 ，然后写入到邮件输出流中，完成之后关闭（连接3）


到这里，我们已经找到了DataSource类对象的两个方法的调用方，而 getOutputStream() 实际并没有调用方，所以可以暂时忽略

问题已经显现出来了，在getContentType()被调用时会创建一个连接同时缓存起来，但是在调用getInputStream()方法是并没有复用已有的连接，而是重新创建一个连接来获取流

这就导致了在getContentType()被调用时创建的连接没有主动关闭，由于此连接是与NG建立的，NG会在连接超过一定时长后就关闭连接（后续抓包也能反应出来），最终导致此连接停留在CLOSE_WAIT状态，同时在垃圾回收器回收之前是不会释放占用的端口。



也就是我们附件发送流程中会存在连接未关闭的情况，而我们服务也上线一段时间了，之前为什么没出现大量增长呢？

通过分析发现，如果邮件中的附件比较少，那么没有被关闭的连接会在发送完邮件之后被YongGC给回收掉

然而，这个CASE比较特殊，一个邮件中有432个附件，每个附件上传的过程是串行的，这就导致这个超大邮件对象的存活时长超过了YongGC阶段，晋升到了老年代

同时，邮件服务器不支持发送包含如此巨大数量附件的邮件，导致发送失败，而定时任务每次都回将其从数据库中取出然后尝试发送邮件

久而久之，老年代中的超大邮件对象越来越多，CLOSE_WAIT状态的TCP连接也越来越多，直到老年代空间不够时，触发FullGC，将其回收掉，同时释放端口资源，所以才会出现CLOSE_WAIT数量达到一定数量后陡然下降的现象
