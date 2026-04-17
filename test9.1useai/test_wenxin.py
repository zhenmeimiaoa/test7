import requests
import json

API_KEY = 'bce-v3/ALTAK-zqkOJUrkULEOgGPi7wzQh/5fbaa9a0656230f2882587f4dbc43ea81b6ba538'
MODEL = 'ernie-4.5-turbo-128k'
URL = 'https://qianfan.baidubce.com/v2/chat/completions'

def ask_ai(symptom, patient='患者'):
    """调用 AI 分析症状"""
    
    prompt = f'''请作为一位经验丰富的全科医生，根据以下患者信息给出就诊建议：

患者姓名：{patient}
症状描述：{symptom}

请按以下格式回复：
🔍【可能病因】- 简要分析可能的病因（2-3种）
🏥【推荐科室】- 建议就诊的科室
⚠️【紧急程度】- 判断：🔴紧急/🟡尽快/🟢可观察
💊【临时处理】- 就诊前可采取的缓解措施
🚨【危险信号】- 如果出现以下情况请立即就医

注意：以上建议仅供参考，不能替代医生面诊。'''

    data = {
        'model': MODEL,
        'messages': [{'role': 'user', 'content': prompt}],
        'temperature': 0.7,
        'max_tokens': 1024
    }
    
    headers = {
        'Authorization': f'Bearer {API_KEY}',
        'Content-Type': 'application/json'
    }
    
    try:
        response = requests.post(URL, headers=headers, json=data, timeout=60)
        result = response.json()
        
        if 'error' in result:
            return f'API错误: {result["error"]}'
            
        choices = result.get('choices', [])
        if choices:
            return choices[0].get('message', {}).get('content', '')
        return '无回复'
        
    except Exception as e:
        return f'请求失败: {e}'

# ===== 交互式问答 =====
print('=== 医疗 AI 助手（输入"quit"退出）===')
print()

while True:
    # 输入症状
    symptom = input('请输入症状描述: ')
    
    if symptom.lower() in ['quit', 'exit', '退出', 'q']:
        print('再见！')
        break
    
    if not symptom.strip():
        print('症状不能为空，请重新输入')
        continue
    
    # 可选：输入患者姓名
    patient = input('患者姓名（直接回车使用"患者"）: ').strip() or '患者'
    
    print()
    print(f'正在分析 {patient} 的症状: {symptom} ...')
    print()
    
    # 调用 AI
    result = ask_ai(symptom, patient)
    
    print('=== AI 诊断建议 ===')
    print(result)
    print()
    print('=' * 50)
    print()