<template>
    <div class="chat-container">
      <!-- 左侧：聊天会话列表 -->
      <div class="sidebar">
        <div class="sidebar-header">
          <span>聊天记录</span>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-plus"
            @click="addNewSession"
            class="add-chat-btn"
            :loading="isLoadingSessions"
          >
            新建对话
          </el-button>
        </div>
        <el-scrollbar class="chat-list" v-loading="isLoadingSessions">
          <div
            v-for="session in chatSessions"
            :key="session.id"
            class="chat-item"
            :class="{ active: currentSessionId === session.id }"
          >
            <div class="chat-item-content" @click="selectSession(session)">
              <div class="session-title">{{ session.sessionTitle }}</div>
              <div class="session-info">
                <span class="last-message">{{ session.lastMessage || '暂无消息' }}</span>
                <span class="update-time">{{ formatTime(session.updateTime) }}</span>
              </div>
            </div>
            <el-button
              type="text"
              size="mini"
              icon="el-icon-delete"
              @click.stop="deleteSession(session.id)"
              class="delete-btn"
            />
          </div>
        </el-scrollbar>
      </div>

      <!-- 右侧：聊天区域 -->
      <div class="chat-window">
        <div class="messages" ref="messageContainer" v-loading="isLoadingHistory">
          <div v-if="chatSessions.length === 0 && !isLoadingSessions" class="empty-state">
            <div class="empty-icon">&#x1F916;</div>
            <div class="empty-text">还没有对话记录</div>
            <div class="empty-hint">点击左侧"新建对话"开始聊天</div>
          </div>
          <div
            v-else
            v-for="(msg, index) in messages"
            :key="index"
            class="message-row"
            :class="msg.role"
          >
            <!-- AI头像 -->
            <div v-if="msg.role === 'assistant'" class="avatar avatar-ai">
              <span>&#x1F916;</span>
            </div>
            <!-- 消息气泡 -->
            <div :class="['bubble', msg.role]" v-html="formatMessageDisplay(msg.content)"></div>
            <!-- 用户头像 -->
            <div v-if="msg.role === 'user'" class="avatar avatar-user">
              <span>&#x1F464;</span>
            </div>
          </div>
        </div>

        <div class="input-bar">
          <el-input
            type="textarea"
            v-model="inputText"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="请输入你的问题..."
            class="chat-input"
            @keyup.enter.native="sendMessage"
            :disabled="isLoading"
          />
          <el-button
            type="primary"
            @click="sendMessage"
            :disabled="isLoading"
            :loading="isLoading"
          >
            {{ isLoading ? '发送中' : '发送' }}
          </el-button>
        </div>
      </div>
    </div>
  </template>

  <script lang="ts">
  import { Component, Vue } from 'vue-property-decorator'
  import { sendChatMessage, getChatSessions, createNewSession, getChatHistory, deleteSession } from '@/api/assistant'
  import { UserModule } from '@/store/modules/user'
  import request from '@/utils/request'

  interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
    timestamp?: number;
  }

  interface ChatSession {
    id: number;           // MySQL中的会话ID
    memoryId: number;     // MongoDB中的memoryId
    sessionTitle: string; // 会话标题
    lastMessage: string;  // 最后一条消息
    updateTime: string;   // 最后更新时间
  }

  @Component
  export default class Chat extends Vue {
    private chatSessions: ChatSession[] = []
    private currentSessionId = 0      // 当前选中的会话ID
    private currentMemoryId = 0       // 当前会话的memoryId
    private messages: ChatMessage[] = []
    private inputText = ''
    private isLoading = false
    private isLoadingSessions = false
    private isLoadingHistory = false

    // 页面首次加载
    async mounted() {
      // 自动获取会话列表
      await this.loadChatSessions()

      // 如果有历史会话，默认选择第一个并加载聊天记录
      if (this.chatSessions.length > 0) {
        await this.selectSession(this.chatSessions[0])
      } else {
        // 没有历史会话，显示欢迎消息
        this.showWelcomeMessage()
      }
    }

    // 加载聊天会话列表（侧边栏显示）
    private async loadChatSessions() {
      try {
        this.isLoadingSessions = true
        const response = await getChatSessions()

        if (response.data.code === 1) {
          this.chatSessions = response.data.data || []
          console.log('加载会话列表成功:', this.chatSessions)
        } else {
          this.$message.error('加载会话列表失败')
        }
      } catch (error) {
        console.error('加载会话列表失败:', error)
        this.$message.error('加载会话列表失败')
      } finally {
        this.isLoadingSessions = false
      }
    }

    // 选择会话（点击侧边栏某一条会话）
    private async selectSession(session: ChatSession) {
      try {
        this.currentSessionId = session.id
        this.currentMemoryId = session.memoryId

        console.log('选择会话:', session.sessionTitle, 'memoryId:', session.memoryId)

        // 加载该会话的完整聊天记录
        await this.loadChatHistory(session.memoryId)

      } catch (error) {
        console.error('选择会话失败:', error)
        this.$message.error('加载聊天记录失败')
      }
    }

    // 加载指定会话的聊天记录
    private async loadChatHistory(memoryId: number) {
      try {
        this.isLoadingHistory = true
        this.messages = [] // 清空当前消息

        const response = await getChatHistory(memoryId)

        if (response.data.code === 1) {
          const { messages } = response.data.data

          if (messages && messages.length > 0) {
            // 显示历史聊天记录，过滤掉无效和空内容的消息
            this.messages = messages
              .map((msg: any) => {
                let content = this.extractMessageContent(msg.content)
                // 如果是用户消息，需要提取纯用户输入部分
                if (msg.role === 'user') {
                  content = this.extractUserInput(content)
                }
                return {
                  role: msg.role,
                  content: content,
                  timestamp: msg.timestamp
                }
              })
              .filter((msg: { role: string, content: string }) => {
                return msg.content && msg.content.trim() !== '' && msg.role !== 'unknown'
              })

            console.log('加载历史记录成功:', this.messages.length, '条消息')
          } else {
            // 没有聊天记录，显示欢迎消息
            this.showWelcomeMessage()
          }

          // 滚动到底部
          this.$nextTick(() => {
            this.scrollToBottom()
          })

        } else {
          this.$message.error('加载聊天记录失败')
          this.showWelcomeMessage()
        }
      } catch (error) {
        console.error('加载聊天记录失败:', error)
        this.showWelcomeMessage()
      } finally {
        this.isLoadingHistory = false
      }
    }

    // 从用户消息中提取纯用户输入，剥离系统上下文
    private extractUserInput(content: string): string {
      // 新格式：【系统上下文】...【用户请求】实际内容
      const newFormatMatch = content.match(/【用户请求】\s*([\s\S]*)/)
      if (newFormatMatch && newFormatMatch[1]) {
        return newFormatMatch[1].trim()
      }
      // 旧格式：当前操作的管理员ID是 X。用户的请求是：实际内容
      const oldFormatMatch = content.match(/用户的请求是：\s*([\s\S]*)/)
      if (oldFormatMatch && oldFormatMatch[1]) {
        return oldFormatMatch[1].trim()
      }
      // 如果都不匹配，原样返回
      return content
    }

    // 提取消息内容 - 处理不同格式的消息内容
    private extractMessageContent(content: any): string {
      // 如果是字符串，检查是否是 TextContent 格式
      if (typeof content === 'string') {
        // 处理 "TextContent { text = "你好" }" 这种字符串格式
        const textContentMatch = content.match(/TextContent\s*\{\s*text\s*=\s*"([^"]*)"\s*\}/)
        if (textContentMatch && textContentMatch[1]) {
          return textContentMatch[1]
        }

        // 处理其他可能的字符串格式，如 text = "内容"
        const textMatch = content.match(/text\s*=\s*"([^"]*)"/)
        if (textMatch && textMatch[1]) {
          return textMatch[1]
        }

        // 普通字符串直接返回
        return content
      }

      // 如果是对象，尝试提取文本内容
      if (content && typeof content === 'object') {
        // 处理对象格式的 TextContent
        if (content.text) {
          return content.text
        }

        // 处理其他可能的对象格式
        if (content.content) {
          return content.content
        }

        // 如果对象有 toString 方法且不是默认的 [object Object]
        const stringified = content.toString()
        if (stringified !== '[object Object]') {
          // 尝试从字符串中提取文本内容
          const textMatch = stringified.match(/text\s*=\s*"([^"]*)"/)
          if (textMatch && textMatch[1]) {
            return textMatch[1]
          }
        }

        // 最后尝试 JSON.stringify
        try {
          return JSON.stringify(content)
        } catch (e) {
          return stringified
        }
      }

      // 其他情况，转换为字符串
      return String(content || '')
    }

    // 显示欢迎消息
    private showWelcomeMessage() {
      this.messages = [
        {
          role: 'assistant',
          content: '你好，我是校园智餐通AI助手，有什么可以帮你？',
          timestamp: Date.now()
        }
      ]
    }

    // 新建会话
    private async addNewSession() {
      try {
        const response = await createNewSession()

        if (response.data.code === 1) {
          const { sessionId, memoryId } = response.data.data

          // 创建新会话对象
          const newSession: ChatSession = {
            id: sessionId,
            memoryId: memoryId,
            sessionTitle: '新对话',
            lastMessage: '你好，我是校园智餐通AI助手，有什么可以帮你？',
            updateTime: new Date().toISOString()
          }

          // 添加到会话列表顶部
          this.chatSessions.unshift(newSession)

          // 切换到新会话
          await this.selectSession(newSession)

          this.$message.success('创建新会话成功')
        } else {
          this.$message.error('创建会话失败')
        }
      } catch (error) {
        console.error('创建会话失败:', error)
        this.$message.error('创建会话失败')
      }
    }

    // 删除会话
    private async deleteSession(sessionId: number) {
      try {
        await this.$confirm('确认删除此会话？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })

        const response = await deleteSession(sessionId)

        if (response.data.code === 1) {
          // 从列表中移除
          this.chatSessions = this.chatSessions.filter(s => s.id !== sessionId)

          // 如果删除的是当前会话，切换到第一个会话或显示欢迎消息
          if (this.currentSessionId === sessionId) {
            if (this.chatSessions.length > 0) {
              await this.selectSession(this.chatSessions[0])
            } else {
              this.currentSessionId = 0
              this.currentMemoryId = 0
              this.showWelcomeMessage()
            }
          }

          this.$message.success('删除成功')
        } else {
          this.$message.error('删除失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除会话失败:', error)
          this.$message.error('删除失败')
        }
      }
    }

    // API 调用方法 - 流式响应处理
    private async sendMessageToAPI(memoryId: number, message: string): Promise<any> {
      try {
        console.log('开始发送消息:', { memoryId, message })

        const response = await fetch('/api/chat/assistant', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'token': UserModule.token
          },
          body: JSON.stringify({
            memoryId: memoryId,
            message: message
          })
        })

        console.log('流式响应状态:', response.status, response.statusText)

        if (!response.ok) {
          const errorText = await response.text()
          console.error('HTTP错误响应:', errorText)
          throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
        }

        // 检查Content-Type来判断是否为流式响应
        const contentType = response.headers.get('content-type')
        console.log('Content-Type:', contentType)

        if (contentType && contentType.includes('application/json')) {
          // JSON响应，直接解析
          const data = await response.json()
          console.log('JSON响应数据:', data)
          // 优先返回结构化数据，否则返回文本回复或字符串化JSON
          if (data.data && (Array.isArray(data.data) || typeof data.data === 'object')) {
            return data.data
          }
          return data.data || data.botReply || JSON.stringify(data)
        } else {
          // 流式响应处理
          if (response.body) {
            const reader = response.body.getReader()
            const decoder = new TextDecoder()
            let fullResponse = ''

            console.log('开始读取流式响应...')

            // 找到当前正在显示"正在思考中..."的消息
            const thinkingMessageIndex = this.messages.length - 1

            while (true) {
              const { done, value } = await reader.read()

              if (done) {
                console.log('流式响应读取完成')
                break
              }

              const chunk = decoder.decode(value, { stream: true })
              fullResponse += chunk
              console.log('接收到chunk:', chunk)

              // 实时更新UI显示
              if (thinkingMessageIndex >= 0 && this.messages[thinkingMessageIndex].role === 'assistant') {
                this.messages[thinkingMessageIndex].content = fullResponse
                // 平滑滚动到底部
                this.scrollToBottomSmooth()
              }
            }

            reader.releaseLock()
            console.log('完整流式响应:', fullResponse)

            // 在流式响应完成后进行格式化
            const finalResponse = fullResponse || '接收到空响应'
            if (thinkingMessageIndex >= 0 && this.messages[thinkingMessageIndex].role === 'assistant') {
              this.messages[thinkingMessageIndex].content = this.formatBotReply(finalResponse)
            }

            return finalResponse
          } else {
            // 普通文本响应
            const textResponse = await response.text()
            console.log('文本响应:', textResponse)
            return textResponse
          }
        }

      } catch (error) {
        console.error('流式响应处理错误:', error)
        throw new Error(`流式响应接收失败: ${error.message}`)
      }
    }

    // 发送消息
    private async sendMessage() {
      const text = this.inputText.trim()
      if (!text || this.isLoading) return

      // 如果没有当前会话，先创建一个
      if (this.currentMemoryId === 0) {
        await this.addNewSession()
        if (this.currentMemoryId === 0) {
          this.$message.error('请先创建会话')
          return
        }
      }

      // 添加用户消息（只存纯文本，不包含系统上下文）
      this.messages.push({
        role: 'user',
        content: text,
        timestamp: Date.now()
      })

      const userMessage = text
      this.inputText = ''
      this.isLoading = true

      try {
        // 添加"正在思考中"提示
        this.messages.push({
          role: 'assistant',
          content: '正在思考中...',
          timestamp: Date.now()
        })
        this.scrollToBottomSmooth()

        // 调用API获取回复（使用当前会话的memoryId）
        const botReply = await this.sendMessageToAPI(this.currentMemoryId, userMessage)

        // 更新最终消息
        const lastMessageIndex = this.messages.length - 1
        if (this.messages[lastMessageIndex].role === 'assistant') {
          const formattedReply = this.formatBotReply(botReply)
          this.messages[lastMessageIndex].content = formattedReply
          this.messages[lastMessageIndex].timestamp = Date.now()
        }
        this.scrollToBottomSmooth()

        // 更新会话列表中的最后消息和时间
        this.updateSessionInList(userMessage)

      } catch (error) {
        // 移除"正在思考中"提示
        if (this.messages.length > 0 && this.messages[this.messages.length - 1].content === '正在思考中...') {
          this.messages.pop()
        }

        // 显示错误消息
        this.messages.push({
          role: 'assistant',
          content: `抱歉，发生了错误：${error.message}`,
          timestamp: Date.now()
        })
        this.scrollToBottomSmooth()

        this.$message.error(`发送失败：${error.message}`)
      } finally {
        this.isLoading = false
      }
    }

    // 更新会话列表中的会话信息
    private updateSessionInList(lastMessage: string) {
      const sessionIndex = this.chatSessions.findIndex(s => s.id === this.currentSessionId)
      if (sessionIndex !== -1) {
        const session = this.chatSessions[sessionIndex]

        // 更新最后消息和时间
        session.lastMessage = lastMessage
        session.updateTime = new Date().toISOString()

        // 如果是新会话（第一条消息），更新标题
        if (!session.lastMessage || session.sessionTitle === '新对话') {
          session.sessionTitle = lastMessage.length > 20 ?
            lastMessage.substring(0, 20) + '...' : lastMessage
        }

        // 将当前会话移到列表顶部
        this.chatSessions.splice(sessionIndex, 1)
        this.chatSessions.unshift(session)
      }
    }

    // 格式化时间显示
    private formatTime(timeStr: string): string {
      if (!timeStr) return ''

      try {
        const date = new Date(timeStr)
        const now = new Date()
        const diffMs = now.getTime() - date.getTime()
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
        const diffDays = Math.floor(diffHours / 24)

        if (diffDays > 0) {
          return `${diffDays}天前`
        } else if (diffHours > 0) {
          return `${diffHours}小时前`
        } else {
          return '刚刚'
        }
      } catch (error) {
        return ''
      }
    }

    private scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageContainer as HTMLElement
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    }

    private scrollToBottomSmooth() {
      this.$nextTick(() => {
        const container = this.$refs.messageContainer as HTMLElement
        if (container) {
          container.scrollTo({
            top: container.scrollHeight,
            behavior: 'smooth'
          })
        }
      })
    }

    // 格式化AI回复内容
    private formatBotReply(data: any): string {
      // 优先处理结构化数据 (JSON)
      if (Array.isArray(data)) {
        console.log('检测到数组，渲染为多行表格')
        // 假设这是一个员工列表
        if (data.length > 0) {
          return this.convertArrayToHtmlTable(data)
        } else {
          return "（返回了一个空列表）"
        }
      }
      if (typeof data === 'object' && data !== null) {
        console.log('检测到对象，渲染为键值对表格')
        // 假设这是一个员工的详细信息
        return this.convertObjectToHtmlTable(data)
      }

      // 处理字符串响应
      if (typeof data === 'string') {
        console.log('原始AI回复内容 (string):', data)
        // 为保持向后兼容，仍然尝试解析Markdown表格
        if (this.isTableContent(data)) {
          console.log('检测到Markdown表格内容，开始转换')
          return this.convertToMarkdownTable(data)
        }
        // 对于普通文本，直接返回
        return data
      }

      // 其他类型的回退处理
      console.log('未知类型，转换为字符串:', data)
      try {
        return JSON.stringify(data)
      } catch (e) {
        return String(data)
      }
    }

    // 检测内容是否包含表格 - 简化版本
    private isTableContent(content: string): boolean {
      // 如果内容包含|符号，就尝试作为表格处理
      const hasPipe = content.includes('|')
      console.log('包含管道符:', hasPipe)

      if (hasPipe) {
        const lines = content.split('\n')
        const pipeLines = lines.filter(line => line.includes('|'))
        console.log('包含|的行数:', pipeLines.length)
        console.log('包含|的行:', pipeLines)

        // 只要有2行以上包含|就认为是表格
        return pipeLines.length >= 2
      }

      return false
    }

    private getHeaderName(key: string): string {
      const headerMap: { [key:string]: string } = {
        'id': 'ID',
        'name': '姓名',
        'username': '用户名',
        'phone': '手机号',
        'status': '状态',
        'sex': '性别',
        'idNumber': '身份证号',
        'createTime': '创建时间',
        'updateTime': '更新时间'
      };
      return headerMap[key] || key;
    }

    private formatValue(key: string, value: any): string {
      if (value === null || value === undefined) return '';
      if (key === 'status') {
        return value === 1 ? '启用' : '禁用';
      }
      if (key === 'sex') {
        return value === 1 ? '男' : (value === 0 ? '女' : '未知');
      }
      return String(value);
    }

    private convertArrayToHtmlTable(array: any[]): string {
      if (array.length === 0) return "返回了空列表。";

      const headers = Object.keys(array[0]);
      let html = '<div class="table-wrapper"><table>';

      // 表头
      html += '<thead><tr>';
      headers.forEach(header => {
        html += `<th>${this.getHeaderName(header)}</th>`;
      });
      html += '</tr></thead>';

      // 表体
      html += '<tbody>';
      array.forEach(row => {
        html += '<tr>';
        headers.forEach(header => {
          html += `<td>${this.formatValue(header, row[header])}</td>`;
        });
        html += '</tr>';
      });
      html += '</tbody></table></div>';

      return html;
    }

    private convertObjectToHtmlTable(obj: { [key: string]: any }): string {
      let html = '<div class="table-wrapper"><table>';
      html += '<thead><tr><th>属性</th><th>值</th></tr></thead>';

      html += '<tbody>';
      for (const key in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, key)) {
          html += '<tr>';
          html += `<td>${this.getHeaderName(key)}</td>`;
          html += `<td>${this.formatValue(key, obj[key])}</td>`;
          html += '</tr>';
        }
      }
      html += '</tbody></table></div>';
      return html;
    }

    // 转换为Markdown表格
    private convertToMarkdownTable(content: string): string {
      try {
        const lines = content.split('\n')
        let tableLines: string[] = []
        let nonTableLines: string[] = []
        let inTable = false

        for (const line of lines) {
          if (line.includes('|')) {
            tableLines.push(line)
            inTable = true
          } else {
            if (inTable && tableLines.length > 0) {
              // 表格结束，处理表格
              const tableHtml = this.buildTableHtml(tableLines)
              nonTableLines.push(tableHtml)
              tableLines = []
              inTable = false
            }
            nonTableLines.push(line)
          }
        }

        // 处理末尾的表格
        if (tableLines.length > 0) {
          const tableHtml = this.buildTableHtml(tableLines)
          nonTableLines.push(tableHtml)
        }

        return nonTableLines.join('\n')
      } catch (error) {
        console.error('表格转换失败:', error)
        return content
      }
    }

    // 构建HTML表格
    private buildTableHtml(tableLines: string[]): string {
      if (tableLines.length < 2) return tableLines.join('\n')

      try {
        const rows = tableLines.map(line =>
          line.split('|').map(cell => cell.trim()).filter(cell => cell !== '')
        )

        if (rows.length === 0) return tableLines.join('\n')

        let html = '<div class="table-wrapper"><table border="1" style="border-collapse: collapse; width: 100%;">'

        // 表头
        if (rows[0]) {
          html += '<thead><tr>'
          rows[0].forEach(cell => {
            const formattedCell = this.formatCellContent(cell)
            html += `<th style="padding: 8px; background: #f5f5f5; border: 1px solid #ddd;">${formattedCell}</th>`
          })
          html += '</tr></thead>'
        }

        // 表体
        html += '<tbody>'
        for (let i = 1; i < rows.length; i++) {
          if (rows[i] && rows[i].length > 0) {
            html += '<tr>'
            rows[i].forEach(cell => {
              const formattedCell = this.formatCellContent(cell)
              html += `<td style="padding: 8px; border: 1px solid #ddd;">${formattedCell}</td>`
            })
            html += '</tr>'
          }
        }
        html += '</tbody></table></div>'

        return html
      } catch (error) {
        console.error('构建表格HTML失败:', error)
        return tableLines.join('\n')
      }
    }

    // 格式化单元格内容
    private formatCellContent(cell: string): string {
      return cell
    }

    // 格式化消息显示
    private formatMessageDisplay(content: string): string {
      return this.formatBotReply(content)
    }
  }
  </script>

  <style scoped lang="scss">
  .chat-container {
    display: flex;
    height: calc(100vh - 60px);
    background: #f0f2f5;

    .sidebar {
      width: 260px;
      border-right: 1px solid #e8e8e8;
      display: flex;
      flex-direction: column;
      background: #fff;

      .sidebar-header {
        padding: 16px;
        font-weight: bold;
        background: #fff;
        border-bottom: 1px solid #f0f0f0;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .add-chat-btn {
          font-size: 12px;
          padding: 4px 8px;
        }
      }

      .chat-list {
        flex: 1;
        padding: 8px;

        .chat-item {
          padding: 10px 12px;
          margin-bottom: 4px;
          border-radius: 8px;
          cursor: pointer;
          transition: background 0.2s;
          display: flex;
          justify-content: space-between;
          align-items: center;

          &:hover {
            background: #f5f5f5;

            .delete-btn {
              opacity: 1;
            }
          }

          &.active {
            background: #e6f4ff;
            font-weight: bold;
          }

          .chat-item-content {
            flex: 1;
            overflow: hidden;

            .session-title {
              font-weight: 500;
              margin-bottom: 4px;
              color: #333;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }

            .session-info {
              display: flex;
              justify-content: space-between;
              align-items: center;
              font-size: 12px;
              color: #999;

              .last-message {
                flex: 1;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                margin-right: 8px;
              }

              .update-time {
                flex-shrink: 0;
                font-size: 11px;
              }
            }
          }

          .delete-btn {
            opacity: 0;
            transition: opacity 0.2s;
            color: #f56c6c;
            padding: 2px 4px;

            &:hover {
              background: #fef0f0;
            }
          }
        }
      }
    }

    .chat-window {
      flex: 1;
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 0;

      .messages {
        flex: 1;
        overflow-y: auto;
        padding: 24px 16px;
        background: #f0f2f5;
        box-sizing: border-box;
        min-height: 0;

        .empty-state {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          height: 100%;
          text-align: center;
          color: #999;

          .empty-icon {
            font-size: 48px;
            margin-bottom: 16px;
          }

          .empty-text {
            font-size: 18px;
            margin-bottom: 8px;
            color: #666;
          }

          .empty-hint {
            font-size: 14px;
            color: #999;
          }
        }

        // 消息行布局
        .message-row {
          display: flex;
          align-items: flex-start;
          margin-bottom: 20px;
          gap: 10px;

          // 用户消息靠右
          &.user {
            flex-direction: row-reverse;

            .bubble.user {
              background: #1677ff;
              color: #fff;
              border-radius: 12px 2px 12px 12px;
            }
          }

          // AI消息靠左
          &.assistant {
            flex-direction: row;

            .bubble.assistant {
              background: #fff;
              color: #333;
              border-radius: 2px 12px 12px 12px;
              box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
            }
          }

          // 头像
          .avatar {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            font-size: 18px;

            &.avatar-ai {
              background: linear-gradient(135deg, #409EFF, #66b1ff);

              span {
                filter: grayscale(0);
              }
            }

            &.avatar-user {
              background: #e8e8e8;
            }
          }

          // 消息气泡
          .bubble {
            max-width: 70%;
            padding: 10px 14px;
            word-break: break-word;
            line-height: 1.7;
            font-size: 14px;
          }
        }
      }

      .input-bar {
        padding: 14px 20px;
        border-top: 1px solid #e8e8e8;
        background: #fff;
        display: flex;
        gap: 10px;
        align-items: flex-end;
        flex-shrink: 0;

        .chat-input {
          flex: 1;
        }
      }
    }
  }

  // Markdown表格样式
  ::v-deep .table-wrapper {
    overflow-x: auto;
    margin: 10px 0;
    border-radius: 6px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }

  ::v-deep table {
    width: 100%;
    border-collapse: collapse;
    background: white;

    th, td {
      border: 1px solid #eee;
      padding: 8px 10px;
      text-align: left;
      vertical-align: middle;
      font-size: 13px;
    }

    th {
      background: #fafafa;
      font-weight: 600;
      color: #333;
    }

    tr:hover td {
      background: #f9f9f9;
    }
  }
  </style>
