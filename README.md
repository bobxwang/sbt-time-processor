##### 说明

- 在需要的地方添加 TimeAnnotation 注解，在编译时会动态修改生成 class 文件，打印出入参跟方法的执行消耗时间
- 机制
  - AbstractProcessor，利用注解修改生成的 class 文件
- 限制点

##### 其它
- 使用 sbt-assembly 打包