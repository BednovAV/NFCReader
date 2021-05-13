package com.example.concept;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pro100svitlo.creditCardNfcReader.CardNfcAsyncTask;
import com.pro100svitlo.creditCardNfcReader.utils.CardNfcUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CardNfcAsyncTask.CardNfcInterface{

    private NfcAdapter mNfcAdapter;
    private CardNfcUtils mCardNfcUtils;
    private boolean mIntentFromCreate;
    private CardNfcAsyncTask mCardNfcAsyncTask;
    private DBHelper dbHelper;
    private LinearLayout listCards;

    // обработчик клика для кнопки удаления
    View.OnClickListener clickDelete = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            for (Card item : cards) {
                if (item.delete_id == v.getId())
                {
                    int delCount = db.delete("mytable", "id = " + item.id, null);
                    break;
                }
            }
            db.close();
            Refresh();
        }
    };

    // обработчик клика для кнопки копирования
    View.OnClickListener clickCopy = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            for (Card item : cards) {
                if (item.copy_id == v.getId())
                {
                    CopyNumber(item.number);
                    break;
                }
            }
            Refresh();

        }
    };

    private class Card {
        public int id;
        public String name;
        public String number;
        public String date;
        public int delete_id;
        public int copy_id;
        public Card(int id, String name, String number, String date)
        {
            this.id = id;
            this.name = name;
            this.number = number.substring(0, 4) + " "
                    + number.substring(4, 8) + " "
                    + number.substring(8, 12) + " "
                    + number.substring(12, 16);
            this.date = date;
        }
    }

    private List<Card> cards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) { }
        else {
            mCardNfcUtils = new CardNfcUtils(this);
            //next few lines here needed in case you will scan credit card when app is closed
            mIntentFromCreate = true;
            onNewIntent(getIntent());
        }

        // view в которую мы будем вставлять карты
        listCards = findViewById(R.id.list_cards);

        // коллекция для карт
        cards = new ArrayList<Card>();

        // создаем объект для создания и управления версиями БД
        dbHelper = new DBHelper(this);

        // вызываем метод отрисовки существующих в памяти карт
        Refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIntentFromCreate = false;
        if (mNfcAdapter != null && !mNfcAdapter.isEnabled()) {
            //show some turn on nfc dialog here. take a look in the samle ;-)
        } else if (mNfcAdapter != null) {
            mCardNfcUtils.enableDispatch();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            mCardNfcUtils.disableDispatch();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            //this - interface for callbacks
            //intent = intent :)
            //mIntentFromCreate - boolean flag, for understanding if onNewIntent() was called from onCreate or not
            mCardNfcAsyncTask = new CardNfcAsyncTask.Builder(this, intent, mIntentFromCreate)
                    .build();
        }


    }

    @Override
    public void startNfcReadCard() {

    }

    @Override
    public void cardIsReadyToRead() {
        String card = mCardNfcAsyncTask.getCardNumber();
        String expiredDate = mCardNfcAsyncTask.getCardExpireDate();
        String cardType = mCardNfcAsyncTask.getCardType();

        if (cardType.equals(CardNfcAsyncTask.CARD_VISA))
        {
            cardType = "VISA";
        }
        else if(cardType.equals(CardNfcAsyncTask.CARD_MASTER_CARD))
        {
            cardType = "MASTER CARD";
        }
        else
        {
            cardType = "UNKNOWN CARD";
        }


        // создаем объект для данных
        ContentValues cv = new ContentValues();

        // подключаемся к БД
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        cv.put("card", card);
        cv.put("date", expiredDate);
        cv.put("name", cardType);
        // вставляем запись и получаем ее ID
        long rowID = db.insert("mytable", null, cv);

        dbHelper.close();

        CopyNumber(card);
    }

    @Override
    public void doNotMoveCardSoFast() {
        Toast.makeText(this,"Держите карту неподвижно", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void unknownEmvCard() {

    }

    @Override
    public void cardWithLockedNfc() {

    }

    @Override
    public void finishNfcReadCard() {
        Refresh();
    }

    // обновляет экран
    private void Refresh() {
        // отчищаем список карт
        listCards.removeAllViews();
        cards.clear();

        // подключаемся к БД
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // делаем запрос всех данных из таблицы mytable, получаем Cursor
        Cursor c = db.query("mytable", null, null, null, null, null, null);

        // ставим позицию курсора на первую строку выборки
        // если в выборке нет строк, вернется false
        if (c.moveToFirst()) {

            // определяем номера столбцов по имени в выборке
            int idColIndex = c.getColumnIndex("id");
            int nameColIndex = c.getColumnIndex("name");
            int cardColIndex = c.getColumnIndex("card");
            int dateColIndex = c.getColumnIndex("date");

            do {
                Card card = new Card(c.getInt(idColIndex), c.getString(nameColIndex),
                        c.getString(cardColIndex), c.getString(dateColIndex));

                cards.add(card);

                AddCardView(card);

                // переход на следующую строку
            } while (c.moveToNext());
        }
        c.close();
    }

    // добавление карты на экран
    private void AddCardView(Card card) {
        // Находим ID макета карты
        int cardView = R.layout.list_item;

        // создаем Inflater, для создания Java оболочки макета
        LayoutInflater inflater = LayoutInflater.from(this);

        // создаем программную оболочку макета карты
        View view = inflater.inflate(cardView, listCards, false);

        // находим  View компоненты макета
        TextView cardNumberView = view.findViewById(R.id.card_number);
        TextView cardDateView = view.findViewById(R.id.card_date);
        ImageButton btn_delete = view.findViewById(R.id.btn_delete);
        ImageButton btn_copy = view.findViewById(R.id.btn_copy);
        TextView card_type = view.findViewById(R.id.card_type);


        // генерируем и устанавливаем ID кнопки удаления для конкретной карты
        card.delete_id = View.generateViewId();
        btn_delete.setId(card.delete_id);


        // генерируем и устанавливаем ID кнопки копирования для конкретной карты
        card.copy_id = View.generateViewId();
        btn_copy.setId(card.copy_id);

        // устанавливаем для кнопок обработчик нажатия
        btn_delete.setOnClickListener(clickDelete);
        btn_copy.setOnClickListener(clickCopy);


        // вставляем данные в макет карты
        cardNumberView.setText(card.number);
        cardDateView.setText(card.date);
        card_type.setText(card.name);

        // добавляем карту на экран
        listCards.addView(view);
    }


    // копирует аргумент в буфер обмена
    private void CopyNumber(String number) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("", number);
        clipboardManager.setPrimaryClip(clipData);
        Toast.makeText(this,"Номер карты скопирован", Toast.LENGTH_SHORT).show();
    }
}
