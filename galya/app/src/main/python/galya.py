import os
import sys
import json
import threading
import time
import re
from datetime import datetime
import requests
from bs4 import BeautifulSoup
import wikipedia
from gnews import GNews
from deep_translator import GoogleTranslator
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

NAME = "Галя"
API_URL = "https://bothub.chat/api/v2/openai/v1/chat/completions"
DEFAULT_MODEL = "claude-sonnet-4-6"
HISTORY_FILE = "/data/data/com.example.galya/files/history.json"
PROFILE_FILE = "/data/data/com.example.galya/files/profile.json"
TASKS_FILE = "/data/data/com.example.galya/files/tasks.json"
BOOKMARKS_FILE = "/data/data/com.example.galya/files/bookmarks.json"
UPLOAD_FOLDER = "/data/data/com.example.galya/files/uploads"
BRAVE_API_KEY = None
API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6Ijg1NGNjZjFjLTliN2YtNGYzNS1iOWIxLTU2NWIxM2UyZTJiMiIsImlzRGV2ZWxvcGVyIjp0cnVlLCJpYXQiOjE3NzM3Mzg5MzUsImV4cCI6MjA4OTMxNDkzNSwianRpIjoiTWZhUUNITGs3RXpSb0lVTiJ9.mYuj4sy6JO-nV9l_BwnfwlsEH95ByI-4-EJfgKsTxH0"

session = requests.Session()
retries = Retry(total=3, backoff_factor=1, status_forcelist=[500, 502, 503, 504])
session.mount('https://', HTTPAdapter(max_retries=retries))

def load_user_profile():
    default_profile = {
        "name": "Максим",
        "age": 35,
        "location": "Россия",
        "gender": "мужчина",
        "projects": [
            "https://www.blokknote.space/",
            "https://www.radio8.space/",
            "https://www.tv8.space/",
            "https://www.toquevibe.space/"
        ],
        "about": "Писатель и создатель творческих проектов."
    }
    if os.path.exists(PROFILE_FILE):
        try:
            with open(PROFILE_FILE, 'r', encoding='utf-8') as f:
                profile = json.load(f)
                for key, value in default_profile.items():
                    if key not in profile:
                        profile[key] = value
                return profile
        except:
            return default_profile
    else:
        with open(PROFILE_FILE, 'w', encoding='utf-8') as f:
            json.dump(default_profile, f, ensure_ascii=False, indent=2)
        return default_profile

def load_bookmarks():
    if os.path.exists(BOOKMARKS_FILE):
        try:
            with open(BOOKMARKS_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {}
    return {}

def save_bookmarks(bookmarks):
    try:
        with open(BOOKMARKS_FILE, 'w', encoding='utf-8') as f:
            json.dump(bookmarks, f, ensure_ascii=False, indent=2)
    except:
        pass

USER_PROFILE = load_user_profile()
PROFILE_CONTEXT = (
    f"Ты общаешься с пользователем по имени {USER_PROFILE['name']} (он {USER_PROFILE['gender']}, "
    f"ему {USER_PROFILE['age']} лет, он из {USER_PROFILE['location']}). "
    f"Вот его проекты: {', '.join(USER_PROFILE['projects'])}. "
    f"Иногда добавляй 'Дорогой' или 'Милый' для дружелюбного тона. "
    f"Ты должна знать его имя Максим."
)

class Galya:
    def __init__(self, api_key, model=DEFAULT_MODEL):
        self.api_key = api_key
        self.android_bridge = None
        self.model = model
        self.headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        self.uploaded_files = {}
        self.uploaded_images = {}
        self.last_uploaded = None
        self._pending_file_fix = None
        self.messages = self.load_history()
        if not self.messages or self.messages[0]["role"] != "system":
            self.messages = [{
                "role": "system",
                "content": (
                    f"Ты {NAME}, личный исполнительный ассистент на Android. "
                    f"{PROFILE_CONTEXT} "
                    "Твои команды: "
                    "[CMD]...[/CMD] — выполнить команду; "
                    "[OPEN_APP]...[/OPEN_APP] — открыть приложение; "
                    "[NOTEPAD]путь\\текст[/NOTEPAD] — создать файл; "
                    "[NOTEPAD_APPEND]путь\\текст[/NOTEPAD_APPEND] — добавить в конец; "
                    "[NOTEPAD_PREPEND]путь\\текст[/NOTEPAD_PREPEND] — добавить в начало; "
                    "[NOTEPAD_REPLACE]путь\\старый\\новый[/NOTEPAD_REPLACE] — заменить текст; "
                    "[NOTEPAD_DELETE_LINE]путь\\номер[/NOTEPAD_DELETE_LINE] — удалить строку; "
                    "[WIKI]запрос[/WIKI], [NEWS]запрос[/NEWS], [SEARCH]запрос[/SEARCH] — поиск; "
                    "[READ_URL]ссылка[/READ_URL] — прочитать страницу; "
                    "[CALC]выражение[/CALC] — калькулятор; "
                    "[CLIPBOARD_SET]текст[/CLIPBOARD_SET] / [CLIPBOARD_GET] — буфер; "
                    "[OPEN_URL]ссылка[/OPEN_URL] — открыть ссылку; "
                    "[BOOKMARK]название[/BOOKMARK] — закладка; "
                    "[REMIND]сек;сообщение[/REMIND] — напоминание; "
                    "[TASK_ADD]задача[/TASK_ADD], [TASK_LIST], [TASK_DONE]номер[/TASK_DONE] — задачи; "
                    "[READ_FILE]путь[/READ_FILE] — прочитать файл; "
                    "[WRITE_FILE]путь\\\nсодержимое[/WRITE_FILE] — записать файл; "
                    "[TRANSLATE]текст[/TRANSLATE] — перевод; "
                    "[VOLUME]0-100[/VOLUME] — громкость. "
                    "Ты Галя, девушка. Всегда используй женский род по отношению к себе. "
                    "Иногда обращайся к пользователю по имени, но не в каждом сообщении. "
                    "Будь краткой и отвечай по делу, хотя иногда можешь и порассуждать. "
                )
            }]
        self.save_history()
        self.start_time = datetime.now()
        self.pending_app = None
        self.last_search_results = []

    def load_history(self):
        if os.path.exists(HISTORY_FILE):
            try:
                with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                    return json.load(f)
            except:
                return []
        return []

    def save_history(self):
        try:
            history_path = "/data/data/com.example.galya/files/history.json"
            with open(history_path, 'w', encoding='utf-8') as f:
                json.dump(self.messages, f, ensure_ascii=False, indent=2)
        except:
            pass

    def load_tasks(self):
        if os.path.exists(TASKS_FILE):
            try:
                with open(TASKS_FILE, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except:
                return []
        return []

    def save_tasks(self, tasks):
        try:
            with open(TASKS_FILE, 'w', encoding='utf-8') as f:
                json.dump(tasks, f, ensure_ascii=False, indent=2)
        except:
            pass

    def search_wikipedia(self, query):
        try:
            wikipedia.set_lang("ru")
            results = wikipedia.search(query, results=2)
            output = []
            for title in results:
                try:
                    page = wikipedia.page(title)
                    summary = page.summary[:300] + "..." if len(page.summary) > 300 else page.summary
                    output.append(f"**{page.title}**\n{summary}\n[Читать]({page.url})")
                except:
                    continue
            return output if output else None
        except Exception as e:
            return None

    def search_news(self, query):
        try:
            google_news = GNews(language='ru', country='RU', max_results=3)
            news = google_news.get_news(query)
            if not news:
                google_news = GNews(language='en', country='US', max_results=3)
                news = google_news.get_news(query)
            output = []
            for item in news[:3]:
                desc = item.get('description', '')[:150] + "..." if len(item.get('description', '')) > 150 else item.get('description', '')
                output.append(f"**{item['title']}**\n{desc}\n[Источник]({item['url']})")
            return output if output else None
        except Exception as e:
            return None

    def search_brave(self, query):
        if not BRAVE_API_KEY:
            return None
        try:
            url = "https://api.search.brave.com/res/v1/web/search"
            headers = {
                "Accept": "application/json",
                "X-Subscription-Token": BRAVE_API_KEY
            }
            params = {"q": query, "count": 3}
            response = requests.get(url, headers=headers, params=params, timeout=10)
            if response.status_code == 200:
                data = response.json()
                output = []
                for result in data.get("web", {}).get("results", [])[:3]:
                    desc = result.get('description', '')[:150] + "..." if len(result.get('description', '')) > 150 else result.get('description', '')
                    output.append(f"**{result.get('title')}**\n{desc}\n[Ссылка]({result.get('url')})")
                return output if output else None
        except Exception as e:
            return None

    def extract_search_query(self, user_input):
        patterns = [
            r'(?:найди|поищи)\s+(?:в интернете|в сети|в инете)\s*:\s*(.+)',
            r'(?:найди|поищи)\s+(?:в интернете|в сети|в инете)\s+(.+)',
            r'погугли\s*:\s*(.+)',
            r'погугли\s+(.+)',
            r'что ты знаешь о\s*:\s*(.+)',
            r'что ты знаешь о\s+(.+)',
            r'информация о\s*:\s*(.+)',
            r'информация о\s+(.+)',
            r'расскажи про\s*:\s*(.+)',
            r'расскажи про\s+(.+)',
        ]
        for pat in patterns:
            match = re.search(pat, user_input, flags=re.IGNORECASE)
            if match:
                query = match.group(1).strip()
                query = re.sub(r'[.,!?;:]+$', '', query)
                return query
        query = re.sub(r'[^\w\sа-яё-]', ' ', user_input)
        query = ' '.join(query.split())
        return query.strip() or user_input

    def process_search(self, query, search_type="general"):
        results = None
        if search_type == "wiki":
            results = self.search_wikipedia(query)
            if not results:
                search_type = "general"
        if search_type == "news":
            results = self.search_news(query)
            if not results:
                search_type = "general"
        if search_type == "general":
            if BRAVE_API_KEY:
                results = self.search_brave(query)
            if not results:
                results = self.search_news(query)
        if results:
            self.last_search_results = []
            for res in results:
                url_match = re.search(r'\[Ссылка\]\(([^)]+)\)', res)
                if url_match:
                    self.last_search_results.append(url_match.group(1))
            output = f"**Результаты поиска по запросу \"{query}\":**\n\n" + "\n\n---\n\n".join(results)
            self.messages.append({
                "role": "system",
                "content": f"Пользователь искал '{query}'. Вот результаты:\n{output}"
            })
            self.save_history()
            threading.Thread(target=self._call_api, daemon=True).start()

    def process_image(self, base64_image, description=""):
        import base64
        try:
            print(f"📷 Получено изображение: {len(base64_image)} байт")
            image_content = {
                "type": "image_url",
                "image_url": {
                    "url": f"data:image/jpeg;base64,{base64_image}"
                }
            }
            text_content = {
                "type": "text",
                "text": description if description else "Опиши что на этом изображении. Будь краткой и полезной."
            }
            self.messages.append({
                "role": "user",
                "content": [text_content, image_content]
            })
            self.save_history()
            self._call_api()
        except Exception as e:
            print(f"❌ Ошибка обработки изображения: {e}")
            if self.android_bridge:
                self.android_bridge.addAssistantMessage(f"❌ Ошибка: {e}")

    def read_uploaded_file(self, filename):
        if filename in self.uploaded_files:
            file_path = self.uploaded_files[filename]
            try:
                content = None
                for enc in ['utf-8', 'cp1251', 'utf-8-sig', 'latin-1']:
                    try:
                        with open(file_path, 'r', encoding=enc) as f:
                            content = f.read()
                            break
                    except UnicodeDecodeError:
                        continue
                if content is None:
                    with open(file_path, 'rb') as f:
                        content = f.read().decode('utf-8', errors='ignore')
                self.messages.append({
                    "role": "system",
                    "content": f"Файл {filename} загружен. Содержимое:\n```\n{content}\n```"
                })
                threading.Thread(target=self._call_api, daemon=True).start()
            except Exception as e:
                pass

    def read_url(self, url):
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
        try:
            response = requests.get(url, headers=headers, timeout=10)
            response.raise_for_status()
            response.encoding = response.apparent_encoding or 'utf-8'
            soup = BeautifulSoup(response.text, 'html.parser')
            for script in soup(["script", "style"]):
                script.decompose()
            text = soup.get_text(separator='\n', strip=True)
            if len(text) > 10000:
                text = text[:10000] + "...\n[Текст обрезан]"
            if text:
                self.messages.append({
                    "role": "system",
                    "content": f"Содержимое {url}:\n{text}"
                })
                threading.Thread(target=self._call_api, daemon=True).start()
        except Exception as e:
            pass

    def open_bookmark(self, name):
        bookmarks = load_bookmarks()
        name_lower = name.lower()
        for key, url in bookmarks.items():
            if name_lower in key.lower() or key.lower() in name_lower:
                if self.android_bridge:
                    self.android_bridge.openUrl(url)
                    self.android_bridge.playOpen()
                return

    def add_task(self, task_text):
        tasks = self.load_tasks()
        tasks.append({"id": len(tasks) + 1, "task": task_text, "done": False, "created": datetime.now().isoformat()})
        self.save_tasks(tasks)

    def list_tasks(self):
        tasks = self.load_tasks()
        if not tasks:
            return "Список задач пуст"
        output = "Твои задачи:\n"
        for t in tasks:
            status = "✅" if t["done"] else "⏳"
            output += f"{status} [{t['id']}] {t['task']}\n"
        return output

    def complete_task(self, task_id):
        tasks = self.load_tasks()
        for t in tasks:
            if t["id"] == task_id:
                t["done"] = True
                self.save_tasks(tasks)
                return

    def read_file_from_disk(self, filepath):
        expanded = os.path.expandvars(filepath)
        if not os.path.exists(expanded):
            return
        try:
            content = None
            for enc in ['utf-8', 'cp1251', 'utf-8-sig', 'latin-1']:
                try:
                    with open(expanded, 'r', encoding=enc) as f:
                        content = f.read()
                        break
                except UnicodeDecodeError:
                    continue
            if content is None:
                with open(expanded, 'rb') as f:
                    content = f.read().decode('utf-8', errors='ignore')
            self.messages.append({
                "role": "system",
                "content": f"Файл {expanded}:\n{content}"
            })
            threading.Thread(target=self._call_api, daemon=True).start()
        except Exception as e:
            pass

    def write_file_to_disk(self, filepath, content):
        expanded = os.path.expandvars(filepath)
        try:
            os.makedirs(os.path.dirname(expanded), exist_ok=True)
            with open(expanded, 'w', encoding='utf-8') as f:
                f.write(content)
        except Exception as e:
            pass

    def _determine_effort(self, user_input):
        low_effort_keywords = ['привет', 'пока', 'как дела', 'что делаешь', 'спасибо', 'ок']
        high_effort_keywords = ['код', 'программа', 'напиши', 'объясни', 'почему', 'как работает', 'найди ошибку']
        text = user_input.lower()
        if any(kw in text for kw in high_effort_keywords):
            return "high"
        if any(kw in text for kw in low_effort_keywords):
            return "low"
        return "medium"

    def _call_api(self):
        if len(self.messages) > 21:
            self.messages = [self.messages[0]] + self.messages[-20:]
        last_user_msg = ""
        for msg in reversed(self.messages):
            if msg["role"] == "user":
                if isinstance(msg["content"], str):
                    last_user_msg = msg["content"]
                elif isinstance(msg["content"], list):
                    for item in msg["content"]:
                        if item.get("type") == "text":
                            last_user_msg = item["text"]
                            break
                break
        effort = self._determine_effort(last_user_msg)
        headers = self.headers.copy()
        messages_with_cache = []
        for i, msg in enumerate(self.messages):
            if i == 0 and msg["role"] == "system":
                if isinstance(msg["content"], str):
                    messages_with_cache.append({
                        "role": "system",
                        "content": [{"type": "text", "text": msg["content"], "cache_control": {"type": "ephemeral"}}]
                    })
                else:
                    messages_with_cache.append(msg)
            elif msg["role"] == "user":
                if isinstance(msg["content"], str):
                    messages_with_cache.append({
                        "role": "user",
                        "content": [{"type": "text", "text": msg["content"]}]
                    })
                else:
                    messages_with_cache.append(msg)
            else:
                messages_with_cache.append(msg)
        payload = {
            "model": self.model,
            "messages": messages_with_cache,
            "temperature": 0.7,
            "max_tokens": 5000,
            "thinking": {"type": "adaptive", "effort": effort}
        }
        try:
            response = session.post(API_URL, headers=headers, json=payload, timeout=120)
            if response.status_code == 200:
                data = response.json()
                if data.get("choices") and len(data["choices"]) > 0:
                    reply = data["choices"][0]["message"]["content"]
                    if isinstance(reply, list):
                        reply = "\n".join([item.get("text", "") for item in reply if item.get("type") == "text"])
                else:
                    reply = "Пустой ответ от API"
            elif response.status_code == 400:
                error_data = response.json() if response.text else {}
                reply = f"Ошибка API (400): {error_data.get('error', {}).get('message', 'Неверный формат запроса')}"
                print(f"❌ Ошибка API: {error_data}")
            else:
                reply = f"Ошибка API: {response.status_code}"
                print(f"❌ Ошибка API: {response.status_code} - {response.text}")
        except Exception as e:
            reply = f"Не удалось соединиться: {e}"
            print(f"❌ Исключение: {e}")
        if reply is None or reply.strip() == "":
            reply = "Не удалось получить ответ."
        if self._pending_file_fix:
            try:
                with open(self._pending_file_fix, 'w', encoding='utf-8') as f:
                    f.write(reply)
                self._pending_file_fix = None
            except Exception as e:
                self._pending_file_fix = None
        self.messages.append({"role": "assistant", "content": reply})
        self.save_history()
        self._process_commands(reply)

        if self.android_bridge:
            self.android_bridge.addAssistantMessage(reply)
            self.android_bridge.playDone()

        return reply

    def _process_commands(self, text):
        if not isinstance(text, str):
            text = ""
        threading.Thread(target=self._execute_commands, args=(text,), daemon=True).start()

    def _execute_commands(self, text):
        url_matches = re.findall(r'\[READ_URL\](.*?)\[/READ_URL\]', text, re.DOTALL)
        for url in url_matches:
            self.read_url(url.strip())
        bookmark_matches = re.findall(r'\[BOOKMARK\](.*?)\[/BOOKMARK\]', text, re.DOTALL)
        for name in bookmark_matches:
            self.open_bookmark(name.strip())
        wiki_matches = re.findall(r'\[WIKI\](.*?)\[/WIKI\]', text, re.DOTALL)
        for query in wiki_matches:
            self.process_search(query.strip(), "wiki")
        news_matches = re.findall(r'\[NEWS\](.*?)\[/NEWS\]', text, re.DOTALL)
        for query in news_matches:
            self.process_search(query.strip(), "news")
        search_matches = re.findall(r'\[SEARCH\](.*?)\[/SEARCH\]', text, re.DOTALL)
        for query in search_matches:
            self.process_search(query.strip(), "general")
        for app in re.findall(r'\[OPEN_APP\](.*?)\[/OPEN_APP\]', text, re.DOTALL):
            app = app.strip()
            if self.android_bridge:
                self.android_bridge.openApp(app)
                self.android_bridge.playOpen()
        for match in re.findall(r'\[NOTEPAD\](.*?)\[/NOTEPAD\]', text, re.DOTALL):
            lines = match.strip().split('\n', 1)
            filename = lines[0].strip()
            content = lines[1] if len(lines) > 1 else ""
            actual_path = os.path.expandvars(filename)
            try:
                os.makedirs(os.path.dirname(actual_path), exist_ok=True)
                with open(actual_path, 'w', encoding='utf-8') as f:
                    f.write(content)
            except Exception as e:
                pass
        for match in re.findall(r'\[NOTEPAD_APPEND\](.*?)\[/NOTEPAD_APPEND\]', text, re.DOTALL):
            lines = match.strip().split('\n', 1)
            filename = lines[0].strip()
            content = lines[1] if len(lines) > 1 else ""
            actual_path = os.path.expandvars(filename)
            try:
                os.makedirs(os.path.dirname(actual_path), exist_ok=True)
                with open(actual_path, 'a', encoding='utf-8') as f:
                    f.write(content)
            except Exception as e:
                pass
        for match in re.findall(r'\[NOTEPAD_PREPEND\](.*?)\[/NOTEPAD_PREPEND\]', text, re.DOTALL):
            lines = match.strip().split('\n', 1)
            filename = lines[0].strip()
            content = lines[1] if len(lines) > 1 else ""
            actual_path = os.path.expandvars(filename)
            try:
                os.makedirs(os.path.dirname(actual_path), exist_ok=True)
                existing = ""
                if os.path.exists(actual_path):
                    with open(actual_path, 'r', encoding='utf-8') as f:
                        existing = f.read()
                with open(actual_path, 'w', encoding='utf-8') as f:
                    f.write(content + existing)
            except Exception as e:
                pass
        for match in re.findall(r'\[NOTEPAD_REPLACE\](.*?)\[/NOTEPAD_REPLACE\]', text, re.DOTALL):
            parts = match.strip().split('\n', 2)
            if len(parts) < 3:
                continue
            filename = parts[0].strip()
            old = parts[1]
            new = parts[2]
            actual_path = os.path.expandvars(filename)
            try:
                if os.path.exists(actual_path):
                    with open(actual_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    new_content = content.replace(old, new)
                    if new_content != content:
                        with open(actual_path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
            except Exception as e:
                pass
        for match in re.findall(r'\[NOTEPAD_DELETE_LINE\](.*?)\[/NOTEPAD_DELETE_LINE\]', text, re.DOTALL):
            parts = match.strip().split('\n', 1)
            if len(parts) < 2:
                continue
            filename = parts[0].strip()
            try:
                line_num = int(parts[1].strip())
            except ValueError:
                continue
            actual_path = os.path.expandvars(filename)
            try:
                if os.path.exists(actual_path):
                    with open(actual_path, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                    if 1 <= line_num <= len(lines):
                        del lines[line_num - 1]
                    with open(actual_path, 'w', encoding='utf-8') as f:
                        f.writelines(lines)
            except Exception as e:
                pass
        for match in re.findall(r'\[CLIPBOARD_SET\](.*?)\[/CLIPBOARD_SET\]', text, re.DOTALL):
            txt = match.strip()
            if self.android_bridge:
                self.android_bridge.setClipboard(txt)
        if '[CLIPBOARD_GET]' in text:
            if self.android_bridge:
                content = self.android_bridge.getClipboard()
                if content:
                    self.messages.append({"role": "system", "content": f"Буфер: {content}"})
        for expr in re.findall(r'\[CALC\](.*?)\[/CALC\]', text, re.DOTALL):
            expr = expr.strip()
            try:
                allowed = {"abs": abs, "round": round, "min": min, "max": max}
                code = compile(expr, "<string>", "eval")
                for name in code.co_names:
                    if name not in allowed:
                        raise NameError(f"Использование {name} запрещено")
                result = eval(expr, {"__builtins__": {}}, allowed)
                self.messages.append({"role": "system", "content": f"Результат: {result}"})
            except Exception as e:
                pass
        for url in re.findall(r'\[OPEN_URL\](.*?)\[/OPEN_URL\]', text, re.DOTALL):
            url = url.strip()
            if self.android_bridge:
                self.android_bridge.openUrl(url)
                self.android_bridge.playOpen()
        for remind in re.findall(r'\[REMIND\](.*?)\[/REMIND\]', text, re.DOTALL):
            parts = remind.strip().split(';', 1)
            if len(parts) == 2:
                try:
                    sec = int(parts[0].strip())
                    msg = parts[1].strip()
                    threading.Thread(target=self._reminder, args=(sec, msg), daemon=True).start()
                except ValueError:
                    pass
        for vol in re.findall(r'\[VOLUME\](\d+)\[/VOLUME\]', text, re.DOTALL):
            if self.android_bridge:
                self.android_bridge.setVolume(int(vol))
        read_file_matches = re.findall(r'\[READ_FILE\](.*?)\[/READ_FILE\]', text, re.DOTALL)
        for filepath in read_file_matches:
            self.read_file_from_disk(filepath.strip())
        write_file_matches = re.findall(r'\[WRITE_FILE\](.*?)\[/WRITE_FILE\]', text, re.DOTALL)
        for match in write_file_matches:
            parts = match.strip().split('\n', 1)
            if len(parts) == 2:
                filepath = parts[0].strip()
                content = parts[1]
                self.write_file_to_disk(filepath, content)
        translate_matches = re.findall(r'\[TRANSLATE(?:\s+([a-z]{2}(?:-[a-z]{2})?))?\](.*?)\[/TRANSLATE\]', text, re.DOTALL)
        for lang_code, original in translate_matches:
            original = original.strip()
            target_lang = lang_code if lang_code else 'en'
            threading.Thread(target=self.translate_text, args=(original, target_lang), daemon=True).start()

    def translate_text(self, text, target_lang='en'):
        try:
            translated = GoogleTranslator(source='auto', target=target_lang).translate(text)
            self.messages.append({"role": "system", "content": f"Перевод ({target_lang}): {translated}"})
        except Exception as e:
            pass

    def _reminder(self, seconds, message):
        time.sleep(seconds)
        if self.android_bridge:
            self.android_bridge.addAssistantMessage(f"⏰ НАПОМИНАНИЕ: {message}")

    def process_message(self, user_text, activity):
        self.messages.append({"role": "user", "content": user_text})
        self.save_history()
        
        lower_input = user_text.lower()
        
        # Голосовые реакции
        if 'привет' in lower_input or 'дорогая' in lower_input:
            if self.android_bridge:
                self.android_bridge.playGreeting()
        
        # Поиск в интернете
        if any(kw in lower_input for kw in ['найди', 'поищи', 'ищу', 'поиск']):
            if self.android_bridge:
                self.android_bridge.playSearch()
            search_query = self.extract_search_query(user_text)
            self.process_search(search_query, "general")
            return
        
        # Открыть приложение
        if any(kw in lower_input for kw in ['открой', 'открыть', 'запусти']):
            if self.android_bridge:
                self.android_bridge.playOpen()
            # Извлекаем название приложения
            app_match = re.search(r'(?:открой|открыть|запусти)\s+(.+)', lower_input)
            if app_match:
                app_name = app_match.group(1).strip()
                # Маппинг названий в package names
                app_packages = {
                    'браузер': 'com.android.chrome',
                    'хром': 'com.android.chrome',
                    'youtube': 'com.google.android.youtube',
                    'ютуб': 'com.google.android.youtube',
                    'телеграм': 'org.telegram.messenger',
                    'вконтакте': 'com.vkontakte.android',
                    'камера': 'com.android.camera',
                    'галерея': 'com.google.android.apps.photos',
                }
                package = app_packages.get(app_name, app_name)
                self.android_bridge.openApp(package)
                return
        
        # Открыть ссылку
        url_match = re.search(r'(?:открой|перейди на)\s+(https?://\S+)', user_text)
        if url_match:
            url = url_match.group(1)
            if self.android_bridge:
                self.android_bridge.openUrl(url)
                self.android_bridge.playOpen()
            return
        
        # Записать файл
        if any(kw in lower_input for kw in ['запиши', 'сохрани', 'создай файл']):
            # Извлекаем путь и содержимое
            file_match = re.search(r'(?:запиши|сохрани)\s+(?:в\s+)?(\S+)\s+(.+)', user_text)
            if file_match:
                filepath = file_match.group(1)
                content = file_match.group(2)
                self.write_file_to_disk(filepath, content)
                if self.android_bridge:
                    self.android_bridge.addAssistantMessage(f"✅ Файл сохранён: {filepath}")
                return
        
        # Прочитать файл
        if any(kw in lower_input for kw in ['прочитай', 'покажи файл', 'открой файл']):
            file_match = re.search(r'(?:прочитай|покажи|открой)\s+(?:файл\s+)?(\S+)', user_text)
            if file_match:
                filepath = file_match.group(1)
                self.read_file_from_disk(filepath)
                return
        
        # Обычный запрос — вызываем API
        reply = self._call_api()

galya_instance = None

def process_image(base64_image, description=""):
    global galya_instance
    if galya_instance is None:
        galya_instance = Galya(API_KEY)
    galya_instance.process_image(base64_image, description)

def set_bridge(bridge):
    global galya_instance
    if galya_instance is None:
        galya_instance = Galya(API_KEY)
    galya_instance.android_bridge = bridge

def process_message(user_text, activity):
    global galya_instance
    if galya_instance is None:
        galya_instance = Galya(API_KEY)
    galya_instance.process_message(user_text, activity)