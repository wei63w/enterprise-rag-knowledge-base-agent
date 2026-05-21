import {
  DeleteOutlined,
  FileTextOutlined,
  MessageOutlined,
  ReloadOutlined,
  SettingOutlined,
  UploadOutlined
} from "@ant-design/icons";
import {
  Alert,
  Badge,
  Button,
  ConfigProvider,
  Input,
  Layout,
  Menu,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Upload
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { deleteDocument, listDocuments, uploadDocument, type DocumentRecord } from "./services/documentApi";
import { sendChat, type SourceReference } from "./services/chatApi";
import "./styles.css";

const { Header, Content, Sider } = Layout;
const { Title, Text } = Typography;

type PageKey = "documents" | "chat" | "settings";

function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDocuments(await listDocuments());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "文档列表加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  async function handleUpload(file: File) {
    setUploading(true);
    setError(null);
    try {
      await uploadDocument(file);
      await loadDocuments();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "文档上传失败");
    } finally {
      setUploading(false);
    }
  }

  async function handleDelete(id: string) {
    setLoading(true);
    setError(null);
    try {
      await deleteDocument(id);
      await loadDocuments();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "文档删除失败");
    } finally {
      setLoading(false);
    }
  }

  const columns: ColumnsType<DocumentRecord> = [
    { title: "文件名", dataIndex: "name", key: "name" },
    { title: "格式", dataIndex: "type", key: "type", width: 120 },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (status: DocumentRecord["status"]) => (
        <Tag color={status === "READY" ? "green" : status === "ERROR" ? "red" : "blue"}>{status}</Tag>
      )
    },
    { title: "切片数", dataIndex: "chunkCount", key: "chunkCount", width: 120 },
    {
      title: "操作",
      key: "actions",
      width: 120,
      render: (_, record) => (
        <Button danger icon={<DeleteOutlined />} size="small" onClick={() => void handleDelete(record.id)}>
          删除
        </Button>
      )
    }
  ];

  return (
    <section className="workspace-panel" aria-labelledby="documents-title">
      <div className="panel-heading">
        <div>
          <Title id="documents-title" level={2}>
            文档
          </Title>
          <Text type="secondary">MVP 文档处理入口</Text>
        </div>
        <Space>
          <Badge status="processing" text="上传后同步解析切片" />
          <Button icon={<ReloadOutlined />} onClick={() => void loadDocuments()}>
            刷新
          </Button>
          <Upload
            accept=".pdf,.md,.txt"
            maxCount={1}
            showUploadList={false}
            beforeUpload={(file) => {
              void handleUpload(file);
              return false;
            }}
          >
            <Button icon={<UploadOutlined />} loading={uploading} type="primary">
              上传文档
            </Button>
          </Upload>
        </Space>
      </div>
      {error ? <Alert className="panel-alert" message={error} showIcon type="error" /> : null}
      <Table
        columns={columns}
        dataSource={documents}
        loading={loading}
        pagination={false}
        rowKey="id"
      />
    </section>
  );
}

function ChatPage() {
  const [messages, setMessages] = useState<Array<{ role: "user" | "assistant"; content: string; sources?: SourceReference[] }>>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSend() {
    if (!input.trim()) return;

    const question = input.trim();
    setInput("");
    setMessages(prev => [...prev, { role: "user", content: question }]);
    setLoading(true);
    setError(null);

    try {
      const response = await sendChat({ question });
      setMessages(prev => [...prev, { role: "assistant", content: response.answer, sources: response.sources }]);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "问答请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="workspace-panel" aria-labelledby="chat-title">
      <div className="panel-heading">
        <div>
          <Title id="chat-title" level={2}>对话</Title>
          <Text type="secondary">RAG 问答工作区</Text>
        </div>
        <Tag color="blue">基础问答</Tag>
      </div>
      {error ? <Alert className="panel-alert" message={error} showIcon type="error" /> : null}
      <div className="chat-surface">
        {messages.length === 0 ? (
          <div className="chat-placeholder">输入问题开始问答</div>
        ) : (
          messages.map((msg, idx) => (
            <div key={idx} className={`message message-${msg.role}`}>
              <div className="message-content">{msg.content}</div>
              {msg.sources && msg.sources.length > 0 ? (
                <div className="message-sources">
                  {msg.sources.map((s, sIdx) => (
                    <div key={sIdx}>
                      <div className="source-header">
                        <Text strong>{s.docName}</Text>
                        <Tag color="blue">相关性: {(s.score * 100).toFixed(1)}%</Tag>
                      </div>
                      <div className="source-content">{s.content}</div>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ))
        )}
      </div>
      <div className="chat-input-area">
        <Input.TextArea
          placeholder="输入问题..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onPressEnter={e => { if (!e.shiftKey) { e.preventDefault(); void handleSend(); } }}
          rows={2}
        />
        <Button type="primary" loading={loading} onClick={() => void handleSend()}>
          发送
        </Button>
      </div>
    </section>
  );
}

function SettingsPage() {
  return (
    <section className="workspace-panel" aria-labelledby="settings-title">
      <div className="panel-heading">
        <div>
          <Title id="settings-title" level={2}>
            配置
          </Title>
          <Text type="secondary">当前 MVP 默认参数</Text>
        </div>
      </div>
      <Space size="large" wrap>
        <Statistic title="向量召回 Top-K" value={8} />
        <Statistic title="切片长度" value={500} />
        <Statistic title="记忆窗口" value={5} />
      </Space>
    </section>
  );
}

function App() {
  const [page, setPage] = useState<PageKey>("documents");

  const content = useMemo(() => {
    if (page === "chat") {
      return <ChatPage />;
    }

    if (page === "settings") {
      return <SettingsPage />;
    }

    return <DocumentsPage />;
  }, [page]);

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 8,
          colorPrimary: "#2364aa",
          fontFamily: "Segoe UI, Microsoft YaHei, sans-serif"
        }
      }}
    >
      <Layout className="app-shell">
        <Sider className="app-sider" width={232}>
          <div className="brand-mark">
            <div className="brand-dot" />
            <span>Enterprise RAG</span>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[page]}
            onClick={({ key }) => setPage(key as PageKey)}
            items={[
              { key: "documents", icon: <FileTextOutlined />, label: "文档" },
              { key: "chat", icon: <MessageOutlined />, label: "对话" },
              { key: "settings", icon: <SettingOutlined />, label: "配置" }
            ]}
          />
        </Sider>
        <Layout>
          <Header className="app-header">
            <Title level={1}>Enterprise RAG Knowledge Base Agent</Title>
            <Text type="secondary">Phase 4 Source Tracing</Text>
          </Header>
          <Content className="app-content">{content}</Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}

export default App;
