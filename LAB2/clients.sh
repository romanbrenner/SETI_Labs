#!/bin/bash

# адрес сервера
HOST="127.0.0.1"
PORT="5000"

# файлы для отправки
FILES=("test.txt" "test2.txt" "test3.txt")

echo "Запуск трех клиентов..."

for FILE in "${FILES[@]}"; do
    echo "Отправка файла: $FILE"
    java FileClient "$HOST" "$PORT" "$FILE" &
done

echo "Все клиенты запущены в фоне."
echo "Ожидаем завершения..."

wait

echo "Все клиенты завершили работу."
